package net.pawet.pawgen.component.system.storage;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
final class Sha1DigestService {

	private static final String ALGORITHM = "SHA-1";
	private static final String ATTR_NAME = "sha1";

	private final MetaService metaService;

	public OutputStream write(Path path, Function<Path, OutputStream> osProvider) throws Exception {
		var os = new DigestOutputStream(osProvider.apply(path), MessageDigest.getInstance(ALGORITHM));
		return new ObservableCloseOutputStream(os, () -> {
			storeDigest(path, os.getMessageDigest().digest());
		});
	}

	@SneakyThrows
	private void storeDigest(Path target, byte[] digest) {
		assert isSha1(digest) : "Not a sha1 digest: " + encode(digest);
		metaService.store(target, ATTR_NAME, digest);
	}

	private boolean isSha1(byte[] digest) {
		return digest != null && digest.length == 20;
	}

	@SneakyThrows
	public byte[] load(Path target) {
		return metaService.load(target, ATTR_NAME)
			.orElseThrow(() -> new IllegalStateException("Can't find attribute '" + ATTR_NAME + "' for " + target));
	}

	private static final int BUFFER_LENGTH = 1024;

	public static byte[] computeSha1(InputStream is) throws NoSuchAlgorithmException, IOException {
		var md = MessageDigest.getInstance(ALGORITHM);
		byte[] buff = new byte[BUFFER_LENGTH];
		int read = is.read(buff, 0, BUFFER_LENGTH);
		while (read > -1) {
			md.update(buff, 0, read);
			read = is.read(buff, 0, BUFFER_LENGTH);
		}
		return md.digest();
	}

	/**
	 * borrowed from apache codecs
	 */
	private static final char[] DIGITS_LOWER = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

	public static String encode(final byte[] data) {
		final int dataLength = data.length;
		final char[] out = new char[dataLength << 1];
		// two characters form the hex value.
		for (int i = 0, j = 0; i < dataLength; i++) {
			out[j++] = DIGITS_LOWER[(0xF0 & data[i]) >>> 4];
			out[j++] = DIGITS_LOWER[0x0F & data[i]];
		}
		return new String(out);
	}


}

final class ObservableCloseOutputStream extends FilterOutputStream {

	private volatile boolean closed;
	private final Object closeLock = new Object();
	private final Runnable observer;

	public ObservableCloseOutputStream(DigestOutputStream os, Runnable closeOnserver) {
		super(os);
		this.observer = closeOnserver;
	}

	@Override
	public void close() throws IOException {
		if (closed) {
			return;
		}
		synchronized (closeLock) {
			if (closed) {
				return;
			}
			closed = true;
			super.close();
			observer.run();
		}
	}

}
