package net.pawet.pawgen.component.system.storage;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;

import static lombok.AccessLevel.PACKAGE;

@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = PACKAGE)
public final class SimpleResource implements Resource {

	@ToString.Include
	@EqualsAndHashCode.Include
	final Path srcPath;
	@ToString.Include
	@EqualsAndHashCode.Include
	final Path destPath;
	final Storage storage;

	@Override
	public ReadableByteChannel readable() {
		return storage.read(srcPath);
	}

	@Override
	public WritableByteChannel writable() {
		return storage.write(destPath);
	}

}
