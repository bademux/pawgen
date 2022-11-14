package net.pawet.pawgen.component.render;

import lombok.RequiredArgsConstructor;
import net.pawet.pawgen.component.Article;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.system.storage.Storage;
import net.pawet.pawgen.component.xml.ArticleParser;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
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
		return Optional.ofNullable(category.getParent()).stream().flatMap(this::getArticles);
	}

	private Collection<Article> readArticlesFor(Category category) {
		try (var headers = articleParser.parse(storage.categoryAwareResource(category))) {
			return headers.collect(Collectors.toList());
		}
	}

	public Stream<Article> getChildren(Category category) {
		return cacheChildren.computeIfAbsent(category, this::readChildrenFor).stream();
	}

	private Collection<Article> readChildrenFor(Category category) {
		try (var headers = storage.readChildren(category.toString()).flatMap(articleParser::parse)) {
			return headers.collect(Collectors.toList());
		}
	}

	public Stream<Article> flatten(Category start) {
		return Stream.concat(getArticles(start), getChildren(start).map(Article::getCategory).flatMap(this::flatten)).distinct();
	}

}
