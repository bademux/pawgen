package net.pawet.pawgen.component.system.storage;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.deployer.digest.CfDigestHandler;
import net.pawet.pawgen.deployer.digest.Sha1DigestHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toMap;

@Slf4j
@RequiredArgsConstructor
final class DigestService {

	private static final String SHA1_ATTR_NAME = "sha1";
	private static final String CFDIGEST_ATTR_NAME = "cfdigest";
	private static final HexFormat HEX_FORMAT = HexFormat.of();

	private final MetaService metaService;

	@SneakyThrows
	public WritableByteChannel write(Path path, WritableByteChannel writableChannel) {
		return new DigestWritableByteChannel(writableChannel,
			new Sha1DigestHandler(digest -> metaService.store(path, SHA1_ATTR_NAME, digest)),
			new CfDigestHandler(path, digest -> metaService.store(path, CFDIGEST_ATTR_NAME, digest))
		);
	}

	public Map<String, String> load(Path target) {
		return Stream.of(SHA1_ATTR_NAME, CFDIGEST_ATTR_NAME)
			.map(attrName -> entry(attrName, formatHex(loadDigest(target, attrName))))
			.collect(toMap(Entry::getKey, Entry::getValue));
	}

	byte[] loadDigest(Path target, String attrName) {
		return metaService.load(target, attrName).orElseThrow(() -> new IllegalStateException("Can't find attribute '" + attrName + "' for " + target));
	}

	static String formatHex(final byte[] data) {
		return data == null ? "<null>" : HEX_FORMAT.formatHex(data);
	}

}

record DigestWritableByteChannel<T extends Consumer<ByteBuffer> & AutoCloseable>(WritableByteChannel channel, T... digestHandlers) implements WritableByteChannel {

	@Override
	public int write(ByteBuffer src) throws IOException {
		for (var digestHandler : digestHandlers) {
			digestHandler.accept(src.duplicate());
		}
		return channel.write(src);
	}

	@Override
	public boolean isOpen() {
		return channel.isOpen();
	}

	@Override
	public void close() throws IOException {
		try {
			channel.close();
		} finally {
			for (var digestHandler : digestHandlers) {
				try {
					digestHandler.close();
				} catch (Exception ignore) {
				}
			}
		}
	}

}
