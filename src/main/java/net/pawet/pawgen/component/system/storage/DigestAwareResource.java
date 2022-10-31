package net.pawet.pawgen.component.system.storage;

import lombok.*;

import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.joining;
import static net.pawet.pawgen.component.system.storage.Sha1DigestService.formatHex;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class DigestAwareResource implements ReadableResource {

	@Getter
	@ToString.Include
	@EqualsAndHashCode.Include
	private final String digest;
	@Getter
	@ToString.Include
	@EqualsAndHashCode.Include
	private final String rootRelativePath;
	private final Supplier<ReadableByteChannel> inputStreamSupplier;

	static DigestAwareResource of(byte[] digest, Path srcPath, Path relativePath, Storage storage) {
		return new DigestAwareResource(formatHex(digest), getRelative(relativePath), () -> storage.read(srcPath));
	}

	private static String getRelative(Path relativePath) {
		return StreamSupport.stream(relativePath.spliterator(), true)
			.map(Path::toString)
			.collect(joining("/"));
	}

	@Override
	public ReadableByteChannel readable() {
		return inputStreamSupplier.get();
	}
}
