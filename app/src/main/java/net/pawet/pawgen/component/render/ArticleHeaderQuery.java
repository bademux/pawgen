package net.pawet.pawgen.component.render;

import lombok.RequiredArgsConstructor;
import net.pawet.pawgen.component.Article;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.system.storage.Storage;
import net.pawet.pawgen.component.xml.ArticleParser;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class ArticleHeaderQuery {

	private final Map<Category, Collection<Article>> cacheArticle = new ConcurrentHashMap<>();
	private final Map<Category, Collection<Article>> cacheChildren = new ConcurrentHashMap<>();
	private final Storage storage;
	private final ArticleParser articleParser;

	public Stream<Article> get(Category category) {
		return cacheArticle.computeIfAbsent(category, this::readArticleByCategory).stream();
	}

	private Collection<Article> readArticleByCategory(Category category) {
		try (var headers = articleParser.parse(storage.categoryAwareResource(category))) {
			return headers.collect(Collectors.toList());
		}
	}

	public Stream<Article> getChildren(Category category) {
		return cacheChildren.computeIfAbsent(category, this::readChildren).stream();
	}

	private Collection<Article> readChildren(Category category) {
		try (var headers = storage.readChildren(category.toString()).flatMap(articleParser::parse)) {
			return headers.collect(Collectors.toList());
		}
	}

}
