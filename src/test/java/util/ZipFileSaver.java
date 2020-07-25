package util;

import lombok.SneakyThrows;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.util.zip.Deflater.BEST_COMPRESSION;

class ZipFileSaver implements AutoCloseable {

	private final ZipOutputStream out;

	@SneakyThrows
	public static ZipFileSaver create(Path zipFile) {
		return new ZipFileSaver(new BufferedOutputStream(Files.newOutputStream(zipFile, CREATE)));
	}

	ZipFileSaver(OutputStream out) {
		this.out = new ZipOutputStream(out, UTF_8);
		this.out.setLevel(BEST_COMPRESSION);
	}

	public final void add(URI uri, String content) throws IOException {
		byte[] data = content.getBytes(UTF_8);
		var zipEntry = new ZipEntry(uri.getPath());
		zipEntry.setSize(data.length);
		zipEntry.setExtra(uri.toString().getBytes(UTF_8));
		out.putNextEntry(zipEntry);
		out.write(data);
		out.closeEntry();
	}

	@SneakyThrows
	@Override
	public void close() {
		out.close();
	}

}
