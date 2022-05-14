package net.pawet.pawgen.component.system.storage;

import lombok.*;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.joining;
import static net.pawet.pawgen.component.system.storage.Sha1DigestService.encode;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class DigestAwareResource implements ReadableResource {

	@Getter
	private final String digest;
	@Getter
	private final String rootRelativePath;
	private final Supplier<InputStream> inputStreamSupplier;

	static DigestAwareResource of(byte[] digest, Path srcPath, Path relativePath, Storage storage) {
		return new DigestAwareResource(encode(digest), getRelative(relativePath), () -> storage.read(srcPath));
	}

	private static String getRelative(Path relativePath) {
		return StreamSupport.stream(relativePath.spliterator(), true)
			.map(Path::toString)
			.collect(joining("/"));
	}

	@Override
	public InputStream inputStream() {
		return inputStreamSupplier.get();
	}
}
