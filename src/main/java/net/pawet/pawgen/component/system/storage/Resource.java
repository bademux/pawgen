package net.pawet.pawgen.component.system.storage;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.joining;
import static lombok.AccessLevel.PRIVATE;

@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = PRIVATE)
public class Resource implements ReadableResource, WritableResource {

	@ToString.Include
	@EqualsAndHashCode.Include
	final Path srcPath;
	@ToString.Include
	@EqualsAndHashCode.Include
	private final Path destPath;
	@Getter
	private final String rootRelativePath;
	private final Storage storage;

	static Resource from(Path srcPath, Path destPath, String relativePath, Storage storage) {
		return new Resource(srcPath, destPath, relativePath, storage);
	}

	static Resource fromSrcWithRelPath(Path srcPath, Path relativePath, Storage storage) {
		return new Resource(srcPath, null, getRelative(relativePath), storage);
	}

	private static String getRelative(Path relativePath) {
		return StreamSupport.stream(relativePath.spliterator(), true)
			.map(Path::toString)
			.collect(joining("/"));
	}

	static Resource fromSrc(Path srcPath, Storage storage) {
		return new Resource(srcPath, null, null, storage);
	}

	@Override
	public InputStream inputStream() {
		return storage.read(srcPath);
	}

	@Override
	public OutputStream outputStream() {
		return storage.write(destPath);
	}

	public Resource withDestPath(String path) {
		Path destPath = storage.resolveOutputDir(path);
		return new Resource(this.srcPath, destPath, this.rootRelativePath, this.storage);
	}
}
