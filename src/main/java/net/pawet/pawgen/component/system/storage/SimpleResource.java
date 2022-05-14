package net.pawet.pawgen.component.system.storage;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
public final class SimpleResource implements Resource {

	@ToString.Include
	@EqualsAndHashCode.Include
	final Path srcPath;
	@ToString.Include
	@EqualsAndHashCode.Include
	private final Path destPath;
	final Storage storage;

	@Override
	public InputStream inputStream() {
		return storage.read(srcPath);
	}

	@Override
	public OutputStream outputStream() {
		return storage.write(destPath);
	}

	public boolean isNewOrChanged() {
		return storage.getModificationDate(srcPath).isAfter(storage.getModificationDate(destPath));
	}

}
