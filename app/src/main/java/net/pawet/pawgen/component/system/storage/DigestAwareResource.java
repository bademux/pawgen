package net.pawet.pawgen.component.system.storage;

import lombok.*;

import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.joining;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class DigestAwareResource implements ReadableResource {

	@ToString.Include
	@EqualsAndHashCode.Include
	private final Map<String, String> digest;
	@Getter
	@ToString.Include
	@EqualsAndHashCode.Include
	private final String path;
	@Getter
	@ToString.Include
	private final long sizeInBytes;
	private final Supplier<ReadableByteChannel> readableSupplier;

	public Optional<String> getDigestBy(String name) {
		return Optional.ofNullable(digest.get(name));
	}

	static DigestAwareResource of(Map<String, String> digests, Path relativePath, long sizeInBytes, Supplier<ReadableByteChannel> readableSupplier) {
		return new DigestAwareResource(digests, getRelative(relativePath), sizeInBytes,  readableSupplier);
	}

	private static String getRelative(Path relativePath) {
		return StreamSupport.stream(relativePath.spliterator(), true)
			.map(Path::toString)
			.collect(joining("/", "/", ""));
	}

	@Override
	public ReadableByteChannel readable() {
		return readableSupplier.get();
	}
}
