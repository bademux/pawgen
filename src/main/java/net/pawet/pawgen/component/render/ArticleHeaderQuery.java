package net.pawet.pawgen.component.render;

import lombok.RequiredArgsConstructor;
import net.pawet.pawgen.component.ArticleHeader;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.system.storage.Storage;
import net.pawet.pawgen.component.xml.HeaderParser;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class ArticleHeaderQuery {

	private final Map<Category, Collection<ArticleHeader>> cacheArticle = new ConcurrentHashMap<>();
	private final Map<Category, Collection<ArticleHeader>> cacheChildren = new ConcurrentHashMap<>();
	private final HeaderParser headerParser = new HeaderParser();
	private final Storage storage;

	public Stream<ArticleHeader> get(Category category) {
		return cacheArticle.computeIfAbsent(category, this::readArticleByCategory).stream();
	}

	private Collection<ArticleHeader> readArticleByCategory(Category category) {
		try (var headers = headerParser.parse(storage.readArticleByCategory(category.toString()))) {
			return headers.collect(Collectors.toList());
		}
	}

	public Stream<ArticleHeader> getChildren(Category category) {
		return cacheChildren.computeIfAbsent(category, this::readChildren).stream();
	}

	private Collection<ArticleHeader> readChildren(Category category) {
		try (var headers = storage.readChildren(category.toString()).flatMap(headerParser::parse)) {
			return headers.collect(Collectors.toList());
		}
	}

}
