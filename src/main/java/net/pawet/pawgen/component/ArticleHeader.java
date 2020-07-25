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
	@Getter
	private final String author;
	@Getter
	private final String date;
	@Getter
	private final String source;
	@Getter
	private final String file;
	@Getter
	private final String fileExt;
	private final Instant lastModifiedTime;
	@Getter
	@NonNull
	private final String url;

	public static ArticleHeader of(Category category, String type, String lang, String title, String author, String date, String source, String file, Instant lastModifiedTime) {
		return new ArticleHeader(category, type, lang, title, author, date, source, file, parseFileExt(file), lastModifiedTime, createUrl(category, title));
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

	public boolean isSameCategory(ArticleHeader articleHeader) {
		return category.equals(articleHeader.category);
	}

	public boolean isSameLang(ArticleHeader articleHeader) {
		return lang.equals(articleHeader.lang);
	}

	public boolean isChildFor(ArticleHeader parent) {
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

	private static final CharsetEncoder CODER = UTF_8.newEncoder();
	private static final String EXT = ".html";

	static String createUrl(Category category, String title) {
		int maxTitleLength = 255 - EXT.length();
		title = '/' + normalizeTitle(title, maxTitleLength) + EXT;
		String url = category.isRoot() ? title : '/' + category.toString() + title;
		url = replaceUnwantedChars(url);
		URI uri = URI.create(url);
		assert !uri.isAbsolute() : "uri should be absolute: " + uri;
		return uri.toString();
	}

	private static String replaceUnwantedChars(String url) {
		return url.replaceAll("[:*?<>|]", "_")
			.replace(' ', '_')
			.replace('"', '\'');
	}

	static String normalizeTitle(String title, int maxTitleLength) {
		int titleLength = title.getBytes(UTF_8).length;
		if (titleLength > maxTitleLength) {
			title = splitStringByByteLength(title, maxTitleLength);
		}
		return title;
	}

	static String splitStringByByteLength(String src, int size) {
		ByteBuffer out = ByteBuffer.allocate(size);
		CODER.encode(CharBuffer.wrap(src), out, true);
		return new String(out.array(), 0, out.position(), UTF_8);
	}

}
