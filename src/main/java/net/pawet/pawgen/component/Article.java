package net.pawet.pawgen.component;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.system.storage.ArticleResource;
import net.pawet.pawgen.component.system.storage.Resource;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static java.nio.charset.StandardCharsets.UTF_8;
import static lombok.AccessLevel.PRIVATE;

@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = PRIVATE)
@Slf4j
public final class Article implements Comparable<Article> {

	private final Resource resource;
	@Getter
	@ToString.Include
	@EqualsAndHashCode.Include
	@NonNull
	private final Category category;
	@Getter
	@ToString.Include
	@NonNull
	private final String type;
	@Getter
	@ToString.Include
	@EqualsAndHashCode.Include
	@NonNull
	private final String lang;
	@Getter
	@ToString.Include
	@EqualsAndHashCode.Include
	@NonNull
	private final String title;
	@Getter
	private final String author;
	@Getter
	private final ZonedDateTime date;
	@Getter
	private final String source;
	@Getter
	private final String file;
	@Getter
	private final String fileExt;
	@Getter
	@NonNull
	private final String url;

	public static Article of(ArticleResource resource, Category category, String type, String lang, String title, String author, ZonedDateTime date, String source, String file) {
		if (date == null) {
			date = ZonedDateTime.ofInstant(resource.getModificationDate(), ZoneOffset.UTC);
		}
		return new Article(resource, category, type, lang, title, author, date, source, file, parseFileExt(file), resource.getUrl());
	}

	@Override
	public int compareTo(Article o) {
		int categoryRes = category.compareTo(o.category);
		if (categoryRes != 0) {
			return categoryRes;
		}
		int langRes = lang.compareTo(o.lang);
		if (langRes != 0) {
			return langRes;
		}
		return title.compareTo(o.title);
	}

	private static String parseFileExt(String file) {
		if (file == null) {
			return null;
		}
		int dotIndex = file.lastIndexOf('.');
		if (dotIndex == -1 || dotIndex == file.length() - 1) {
			return null;
		}
		return file.substring(dotIndex + 1).toLowerCase();
	}

	public boolean isSameCategory(Article article) {
		return category.equals(article.category);
	}

	public boolean isSameLang(Article article) {
		return lang.equals(article.lang);
	}

	public boolean isChildFor(Article parent) {
		return category.isChildFor(parent.getCategory());
	}

	public String relativize(String url) {
		if (url == null) {
			return null;
		}
		return category.relativize(normalize(url));
	}

	private String normalize(String url) {
		try {
			return new URI(url).normalize().toASCIIString();
		} catch (URISyntaxException e) {
			//TODO: fix url in articles and make throw exception if invalid article
			log.warn("bad url '{}' for article: '{}'", url, title);
		}
		return url;
	}

	@SneakyThrows
	public CharSequence readContent() {
		try (var is = resource.inputStream()) {
			return new String(is.readAllBytes(), UTF_8);
		}
	}

	public Writer writer() {
		return new OutputStreamWriter(resource.outputStream(), UTF_8);
	}

}
