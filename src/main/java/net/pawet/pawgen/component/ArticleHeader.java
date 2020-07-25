package net.pawet.pawgen.component;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.time.Instant;

import static java.nio.charset.StandardCharsets.UTF_8;
import static lombok.AccessLevel.PRIVATE;

@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = PRIVATE)
@Slf4j
public final class ArticleHeader implements Comparable<ArticleHeader> {
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
	private final String author;
	private final String date;
	private final String source;
	private final String file;
	private final Instant lastModifiedTime;
	@Getter
	@NonNull
	private final String url;

	public static ArticleHeader of(String[] categories, String type, String lang, String title, String author, String date, String source, String file, Instant lastModifiedTime) {
		Category category = Category.of(categories);
		return new ArticleHeader(category, type, lang, title, author, date, source, file, lastModifiedTime, createUrl(category, title));
	}

	@Override
	public int compareTo(ArticleHeader o) {
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

	private String getFileExt() {
		if (file == null) {
			return null;
		}
		int dotIndex = file.lastIndexOf('.');
		if (dotIndex == -1 || dotIndex == file.length() - 1) {
			return null;
		}
		return file.substring(dotIndex + 1).toLowerCase();
	}

	public boolean isSameCategory(ArticleHeader articleHeader) {
		return category.equals(articleHeader.category);
	}

	public boolean isSameLang(ArticleHeader articleHeader) {
		return lang.equals(articleHeader.lang);
	}

	public boolean isParentFor(ArticleHeader child) {
		return child.isChildFor(this);
	}

	public boolean isChildFor(ArticleHeader parent) {
		return category.isChildFor(parent.getCategory());
	}

	public final String get(String propName) {
		switch (propName) {
			case "author":
				return author;
			case "category":
				return category.toString();
			case "date":
				return date;
			case "file":
				return file;
			case "fileExt":
				return getFileExt();
			case "lang":
				return lang;
			case "source":
				return source;
			case "title":
				return (".".equals(title) || category.toString().endsWith("_space")) ? "" : title;
			case "type":
				return type;
			case "url":
				return url;
			default:
		}
		return null;
	}


	public final CharSequence relativize(String url) {
		if (url == null) {
			return url;
		}
		return category.relativize(normalize(url));
	}

	private String normalize(String url) {
		try {
			return new URI(url).normalize().toASCIIString();
		} catch (URISyntaxException e) {
			//TODO: fix url in articles and make throw exception if invalid article
			log.error("bad url for article {}", this, e);
		}
		return url;
	}

	private static final CharsetEncoder CODER = UTF_8.newEncoder();
	private static final String EXT = ".html";

	static String createUrl(Category category, String title) {
		int maxTitleLength = 255 - EXT.length();
		String t = '/' + normalizeTitle(title, maxTitleLength) + EXT;
		return category.isRoot() ? t : '/' + category.toString() + t;
	}

	static String normalizeTitle(String title, int maxTitleLength) {
		int titleLength = title.getBytes(UTF_8).length;
		if (titleLength > maxTitleLength) {
			title = splitStringByByteLength(title, maxTitleLength);
		}
		return title.replace(' ', '_').replace('"', '\'');
	}

	static String splitStringByByteLength(String src, int size) {
		ByteBuffer out = ByteBuffer.allocate(size);
		CODER.encode(CharBuffer.wrap(src), out, true);
		return new String(out.array(), 0, out.position(), UTF_8);
	}

}
