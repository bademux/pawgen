package net.pawet.pawgen.deployer.digest;

import lombok.RequiredArgsConstructor;
import org.bouncycastle.jcajce.provider.digest.SHA1;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.function.Consumer;

@RequiredArgsConstructor
public final class Sha1DigestHandler implements Consumer<ByteBuffer>, AutoCloseable {
	private final MessageDigest mdSha1 = new SHA1.Digest();
	private final Consumer<byte[]> consumer;

	@Override
	public void accept(ByteBuffer bb) {
		mdSha1.update(bb);
	}

	@Override
	public void close() {
		consumer.accept(mdSha1.digest());
	}

}
