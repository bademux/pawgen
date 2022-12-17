package net.pawet.pawgen.deployer.digest;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.digests.Blake3Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.io.DigestOutputStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.BiFunction;

import static java.nio.file.StandardOpenOption.READ;
import static net.pawet.pawgen.deployer.digest.CfDigestHandler.parseFileExt;

@Slf4j
@RequiredArgsConstructor
public final class DigestValidator {

	private static final HexFormat HEX_FORMAT = HexFormat.of();
	private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
	private final BiFunction<Path, String, byte[]> loadDigest;


	public boolean assertChecksum(Path path) {
		var digest = calculate(path);
		String digestSha1 = formatHex(loadDigest.apply(path, "sha1")), calculatedSha1 = formatHex(digest.get("sha1"));
		String digestCf = formatHex(loadDigest.apply(path, "cfdigest")), calculatedCf = formatHex(digest.get("cfdigest"));
		if (digestSha1.equals(calculatedSha1)) {
			return true;
		}
		log.error("Digest error {}: got sha1[{}], expected sha1[{}], got cf[{}], expected cf[{}].", path, digestSha1, calculatedSha1, digestCf, calculatedCf);
		return false;
	}

	@SneakyThrows
	static Map<String, byte[]> calculate(Path path) {
		try (var in = Files.newInputStream(path, READ)) {
			var outSha1 = new DigestOutputStream(new SHA1Digest());
			var outBlake3 = new DigestOutputStream(new Blake3Digest(128));
			var outBase64 = BASE64_ENCODER.wrap(outBlake3);
			byte[] buffer = new byte[8192];
			int read;
			while ((read = in.read(buffer, 0, buffer.length)) >= 0) {
				outSha1.write(buffer, 0, read);
				outBase64.write(buffer, 0, read);
			}
			outBase64.close();
			outBlake3.write(parseFileExt(path.getFileName().toString()).getBytes());
			return Map.of(
				"sha1", outSha1.getDigest(),
				"cfdigest", outBlake3.getDigest()
			);
		}
	}

	static String formatHex(final byte[] data) {
		return data == null ? "<null>" : HEX_FORMAT.formatHex(data);
	}

}
