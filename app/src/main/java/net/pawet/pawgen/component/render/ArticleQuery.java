package net.pawet.pawgen.component.render;

import lombok.RequiredArgsConstructor;
import net.pawet.pawgen.component.Article;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.system.storage.Storage;
import net.pawet.pawgen.component.xml.ArticleParser;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;

@RequiredArgsConstructor
public class ArticleQuery {

	private final Map<Category, Collection<Article>> cacheArticle = new ConcurrentHashMap<>();
	private final Map<Category, Collection<Article>> cacheChildren = new ConcurrentHashMap<>();
	private final Map<Category, Collection<Article>> cacheNewest = new ConcurrentHashMap<>();
	private final Storage storage;
	private final ArticleParser articleParser;

	public Stream<Article> getLast(Category category, ZonedDateTime toDate, int limit) {
		return cacheNewest.computeIfAbsent(category, c -> readLastFor(c, toDate, limit)).stream();
	}

	private List<Article> readLastFor(Category category, ZonedDateTime toDate, int limit) {
		return flatten(category).filter(article -> !category.equals(article.getCategory()))
			.filter(article -> isBeforeOrEqual(article.getDate(), toDate))
			.distinct()
			.sorted(comparing(Article::getDate).reversed())
			.limit(limit)
			.toList();
	}

	private static boolean isBeforeOrEqual(ZonedDateTime date, ZonedDateTime toDate) {
		return date.isBefore(toDate) || date.isEqual(toDate);
	}

	public Stream<Article> getArticles(Category category) {
		return cacheArticle.computeIfAbsent(category, this::readArticlesFor).stream();
	}

	public Stream<Article> getParents(Category category) {
		return Stream.ofNullable(category.getParent()).flatMap(this::getArticles);
	}

	private Collection<Article> readArticlesFor(Category category) {
		try (var resources = storage.read(category)) {
			return resources.map(articleParser::parse).toList();
		}
	}

	public Stream<Article> getChildren(Category category) {
		return cacheChildren.computeIfAbsent(category, this::readChildrenFor).stream().sorted();
	}

	private Collection<Article> readChildrenFor(Category category) {
		try (var resources = storage.readChildren(category)) {
			return resources.map(articleParser::parse).toList();
		}
	}

	public Stream<Article> flatten(Category start) {
		return Stream.concat(getArticles(start), getChildren(start).map(Article::getCategory).flatMap(this::flatten)).distinct();
	}

}
