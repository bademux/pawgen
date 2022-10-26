package net.pawet.pawgen.component.system.storage;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Delegate;
import net.pawet.pawgen.component.Category;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.time.Instant;
import java.util.function.UnaryOperator;

import static java.nio.charset.StandardCharsets.UTF_8;
import static lombok.AccessLevel.PROTECTED;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = PROTECTED)
public final class ArticleResource implements Resource {

	@Delegate(types = {ReadableResource.class, WritableResource.class})
	private final Resource resource;
	@Getter
	private final Resource attachment;
	@Getter
	private final String attachmentPath;
	@Getter
	private final String attachmentType;
	@Getter
	private final Instant modificationDate;
	@Getter
	private final String url;

	private static final CharsetEncoder CODER = UTF_8.newEncoder();
	private static final String EXT = ".html";

	public static ArticleResource of(CategoryAwareResource resource, String title, String file, UnaryOperator<InputStream> contentSupplier) {
		String url = createUrl(resource.getCategory(), title);
		return new ArticleResource(new Resource() {
			final Resource res = resource.storage.resource(resource.srcPath, url).orElseThrow();

			@Override
			public InputStream inputStream() {
				return contentSupplier.apply(res.inputStream());
			}

			@Override
			public OutputStream outputStream() {
				return res.outputStream();
			}
		},resource.storage.resource(file).orElse(Resource.EMPTY), file, parseFileExt(file), resource.getModificationDate(), url);
	}

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


	static String parseFileExt(String file) {
		if (file == null) {
			return null;
		}
		int dotIndex = file.lastIndexOf('.');
		if (dotIndex == -1 || dotIndex == file.length() - 1) {
			return null;
		}
		return file.substring(dotIndex + 1).toLowerCase();
	}
}
