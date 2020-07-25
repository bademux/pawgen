package net.pawet.pawgen.component.render;

import com.github.mustachejava.MustacheException;
import com.github.mustachejava.MustacheResolver;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.Files.*;
import static java.util.Objects.requireNonNull;

/**
 * MustacheResolver implementation that resolves
 * mustache files from the filesystem.
 */
class PathFileSystemResolver implements MustacheResolver {

	private final Path fileRoot;
	private final Path defaultTemplate;

	/**
	 * Use the file system to resolve mustache templates.
	 *
	 * @param fileRoot where in the file system to find the templates
	 */
	public PathFileSystemResolver(Path fileRoot, String defaultTemplateName) {
		if (notExists(requireNonNull(fileRoot))) {
			throw new MustacheException(fileRoot + " does not exist");
		}
		if (!isDirectory(fileRoot)) {
			throw new MustacheException(fileRoot + " is not a directory");
		}
		this.fileRoot = fileRoot.toAbsolutePath().normalize();
		defaultTemplate = defaultTemplateName == null ? null : fileRoot.resolve(defaultTemplateName);
	}

	@Override
	public Reader getReader(String resourceName) {
		Path file = getFile(resourceName);
		if (Files.notExists(file)) {
			file = defaultTemplate;
		}
		if (!Files.isRegularFile(file)) {
			return null;
		}
		checkIsRoot(file);
		try {
			return newBufferedReader(file);
		} catch (IOException e) {
			throw new MustacheException("Found file, could not open: " + file, e);
		}
	}

	/**
	 * Check to make sure that the file is under the file root or current directory.
	 * Without this check you might accidentally open a security whole when exposing
	 * mustache templates to end users.
	 *
	 * @param file
	 */
	private void checkIsRoot(Path file) {
		if (!file.toAbsolutePath().normalize().startsWith(fileRoot)) {
			throw new MustacheException("File not under root: " + fileRoot);
		}
	}

	private Path getFile(String resourceName) {
		return fileRoot == null ? Paths.get(resourceName) : fileRoot.resolve(resourceName);
	}
}
