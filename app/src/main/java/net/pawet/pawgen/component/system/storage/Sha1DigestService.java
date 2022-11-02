package net.pawet.pawgen.component.system.storage;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Consumer;

import static java.nio.file.StandardOpenOption.READ;

@Slf4j
@RequiredArgsConstructor
final class Sha1DigestService {

	private static final String ALGORITHM = "SHA-1";
	private static final String ATTR_NAME = "sha1";
	public static final HexFormat HEX_FORMAT = HexFormat.of();

	private final MetaService metaService;

	@SneakyThrows
	public WritableByteChannel write(Path path, WritableByteChannel out) {
		return new DigestWritableByteChannel(MessageDigest.getInstance(ALGORITHM), out, digest -> storeDigest(path, digest));
	}

	public boolean assertChecksum(Path path) {
		String digest = formatHex(load(path)), calculated = formatHex(calculateSha1(path));
		if (digest.equals(calculated)) {
			return true;
		}
		log.error("Digest error: got {}, expected {} - {}", digest, calculated, path);
		return false;
	}

	@SneakyThrows
	private static byte[] calculateSha1(Path path) {
		try (var io = Files.newInputStream(path, READ)) {
			var os = new DigestOutputStream(OutputStream.nullOutputStream(), MessageDigest.getInstance(ALGORITHM));
			io.transferTo(os);
			os.flush();
			return os.getMessageDigest().digest();
		}
	}

	@SneakyThrows
	private void storeDigest(Path target, byte[] digest) {
		assert isSha1(digest) : "Not a sha1 digest: " + formatHex(digest);
		metaService.store(target, ATTR_NAME, digest);
	}

	private static boolean isSha1(byte[] digest) {
		return digest != null && digest.length == 20;
	}

	@SneakyThrows
	public byte[] load(Path target) {
		return metaService.load(target, ATTR_NAME)
			.orElseThrow(() -> new IllegalStateException("Can't find attribute '" + ATTR_NAME + "' for " + target));
	}

	public static String formatHex(final byte[] data) {
		return HEX_FORMAT.formatHex(data);
	}


}

@RequiredArgsConstructor
final class DigestWritableByteChannel implements WritableByteChannel {
	private final MessageDigest md;
	private final WritableByteChannel channel;
	private final Consumer<byte[]> digestListener;

	@Override
	public int write(ByteBuffer src) throws IOException {
		int position = src.position();
		md.update(src);
		src.position(position);
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
			digestListener.accept(md.digest());
		}
	}
}
