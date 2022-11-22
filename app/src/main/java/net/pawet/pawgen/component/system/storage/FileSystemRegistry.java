package net.pawet.pawgen.component.system.storage;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.file.Files.createDirectories;
import static java.util.Map.entry;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;

@Slf4j
public class FileSystemRegistry implements AutoCloseable {


	public static final String DIR_CONTENT_POSTFIX = "**";
	private final Set<FileSystem> fileSystems = ConcurrentHashMap.newKeySet();

	@SneakyThrows
	public Path getPathFsRegistration(URI uri) {
		String fragment = uri.getFragment();
		uri = new URI(uri.getScheme(), uri.getSchemeSpecificPart(), null);
		try {
			return Path.of(uri);
		} catch (FileSystemNotFoundException ignore) {
		}
		Map<String, String> env = parseQueryParams(fragment).collect(toMap(Entry::getKey, Entry::getValue));
		FileSystem fs = createFS(uri, env);
		fileSystems.add(fs);
		return fs.provider().getPath(uri);
	}

	private FileSystem createFS(URI uri, Map<String, ?> env) throws IOException {
		try {
			return FileSystems.newFileSystem(uri, env);
		} catch (NoSuchFileException e) {
			createDirsIfNeeded(Path.of(e.getFile()).getParent());
			return FileSystems.newFileSystem(uri, env);
		}
	}

	private static void createDirsIfNeeded(Path dir) throws IOException {
		if (dir != null && Files.notExists(dir)) {
			createDirectories(dir);
		}
	}

	public static Stream<Entry<String, String>> parseQueryParams(String params) {
		return Optional.ofNullable(params)
			.filter(not(String::isBlank))
			.stream()
			.flatMap(Pattern.compile("&")::splitAsStream)
			.map(String::strip)
			.map(Pattern.compile("=")::split)
			.filter(arr -> arr.length == 2)
			.map(strings -> entry(strings[0], strings[1]));
	}

	public Stream<Entry<Path, Path>> parseCopyDir(URI path) {
		return Optional.of(path)
			.filter(uri -> uri.getSchemeSpecificPart().endsWith(DIR_CONTENT_POSTFIX))
			.map(FileSystemRegistry::stripDirContentPostfix)//new URI
			.map(this::list)
			.orElseGet(() -> listFromParent(path));
	}

	@SneakyThrows
	private Stream<Entry<Path, Path>> listFromParent(URI uri) {
		Path baseDir = getPathFsRegistration(uri);
		Path parent = baseDir.getParent();
		return list(baseDir, parent == null ? baseDir : parent);
	}

	@SneakyThrows
	private Stream<Entry<Path, Path>> list(URI uri) {
		Path baseDir = getPathFsRegistration(uri);
		return list(baseDir, baseDir);
	}

	private static Stream<Entry<Path, Path>> list(Path baseDir, Path relativeTo) throws IOException {
		return Files.walk(baseDir).filter(Files::isRegularFile).map(p -> Map.entry(relativeTo.relativize(p), p));
	}

	@SneakyThrows
	private static URI stripDirContentPostfix(URI uri) {
		String ssp = uri.getSchemeSpecificPart();
		return new URI(uri.getScheme(), ssp.substring(0, ssp.length() - DIR_CONTENT_POSTFIX.length()), uri.getFragment());
	}

	@Override
	public void close() {
		for (FileSystem entry : fileSystems) {
			if (!entry.isOpen()) {
				continue;
			}
			try {
				entry.close();
			} catch (IOException ignore) {
			}
		}
	}

}

