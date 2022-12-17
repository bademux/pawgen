package net.pawet.pawgen.deployer.digest;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.bouncycastle.crypto.digests.Blake3Digest;
import org.bouncycastle.crypto.io.DigestOutputStream;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * CloudFlare Pages <a href="https://github.com/cloudflare/workers-sdk/blob/95585ce/packages/wrangler/src/pages/hash.ts#L10">digest spec</a>
 */
@RequiredArgsConstructor
public final class CfDigestHandler implements Consumer<ByteBuffer>, AutoCloseable {
	private final DigestOutputStream digestOs = new DigestOutputStream(new Blake3Digest(128));
	private final OutputStream base64Os = Base64.getEncoder().wrap(digestOs);
	private final Path path;
	private final Consumer<byte[]> consumer;

	@SneakyThrows
	@Override
	public void accept(ByteBuffer bb) {
		if (bb.hasArray()) {
			base64Os.write(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining());
			return;
		}
		byte[] src = new byte[bb.remaining()];
		bb.get(src);
		base64Os.write(src);
	}

	@SneakyThrows
	@Override
	public void close() {
		base64Os.close(); //flush stream
		digestOs.write(parseFileExt(path.getFileName().toString()).getBytes()); //append non base64 ending
		consumer.accept(digestOs.getDigest());
	}

	static String parseFileExt(@NonNull String fileName) {
		int dotIndex = fileName.lastIndexOf('.');
		if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
			return "";
		}
		return fileName.substring(dotIndex + 1);
	}

}
