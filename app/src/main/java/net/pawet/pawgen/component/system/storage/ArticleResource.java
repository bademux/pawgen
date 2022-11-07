package net.pawet.pawgen.component.system.storage;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import net.pawet.pawgen.component.Category;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharsetEncoder;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static lombok.AccessLevel.PROTECTED;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = PROTECTED)
public final class ArticleResource implements ReadableResource {

	private static final CharsetEncoder CODER = UTF_8.newEncoder();
	private static final String EXT = ".html";

	@Getter
	private final Category category;
	@ToString.Include
	@EqualsAndHashCode.Include
	final Path srcPath;
	final Storage storage;

	@Override
	public ReadableByteChannel readable() {
		return storage.read(srcPath);
	}

	public Instant getModificationDate() {
		return storage.getModificationDate(srcPath);
	}

	public WritableByteChannel writableFor(String title) {
		return storage.resource(srcPath, createUrl( category,  title)).orElseThrow().writable();
	}

	public String urlFor(String title) {
		return createUrl(category, title);
	}

	public Optional<AttachmentResource> attachment(String file) {
		return Optional.ofNullable(file)
			.flatMap(storage::resource)
			.map(resource -> AttachmentResource.of(resource, category.relativize(file)));
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
		return title.toLowerCase();
	}

	static String splitStringByByteLength(String src, int size) {
		ByteBuffer out = ByteBuffer.allocate(size);
		CODER.encode(CharBuffer.wrap(src), out, true);
		return new String(out.array(), 0, out.position(), UTF_8);
	}

}
