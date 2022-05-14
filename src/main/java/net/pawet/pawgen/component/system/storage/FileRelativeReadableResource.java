package net.pawet.pawgen.component.system.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.joining;

public interface FileRelativeReadableResource extends ReadableResource {


	static FileRelativeReadableResource from(Path srcPath, Path relativePath, Storage storage) {
		return new FileRelativeReadableResource() {
			public String getRelativeSrc() {
				return getRelative(relativePath);
			}

			public InputStream inputStream() {
				return storage.read(srcPath);
			}
		};
	}

	private static String getRelative(Path relativePath) {
		return StreamSupport.stream(relativePath.spliterator(), true)
			.map(Path::toString)
			.collect(joining("/"));
	}

	String getRelativeSrc();

	InputStream inputStream();
}
