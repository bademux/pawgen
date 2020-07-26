package net.pawet.pawgen.component;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Stream;

import static java.lang.Integer.MAX_VALUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.FileVisitOption.FOLLOW_LINKS;
import static java.nio.file.Files.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.*;
import static java.util.function.Predicate.not;
import static lombok.AccessLevel.PRIVATE;

@Log
@RequiredArgsConstructor(staticName = "of")
public class Storage implements AutoCloseable {

	public static final String XML_FILENAME = "index.xml";
	private final Path contentDir;
	private final Path outputDir;
	private final Path staticDir;
	private final Instant dateFrom;

	@SneakyThrows
	public void copyStaticResources() {
		try (Stream<Path> paths = find(staticDir, MAX_VALUE, this::isLatestFile, FOLLOW_LINKS)) {
			paths.filter(not(this::skipKnownDirs))
				.forEach(this::copyToStaticDir);
		}
	}

	private boolean skipKnownDirs(Path path) {
		return path.startsWith(contentDir);
	}

	private boolean isLatestFile(Path path, BasicFileAttributes attrs) {
		return Files.isRegularFile(path) && filterLatest(attrs);
	}

	private void copyToStaticDir(Path src) {
		try {
			Files.copy(src, createDestPath(src), REPLACE_EXISTING);
		} catch (IOException e) {
			log.log(Level.SEVERE, e, () -> "Can't copy file '{}' to staticDir" + src);
		}
	}

	private Path createDestPath(Path src) throws IOException {
		Path dest = outputDir.resolve(staticDir.relativize(src));
		Path destDir = dest.getParent();
		if (destDir != null && Files.notExists(destDir)) {
			Files.createDirectories(destDir);
		}
		return dest;
	}

	@SneakyThrows
	public Stream<Readable> readArticles() {
		Stream<Path> paths = find(contentDir, MAX_VALUE, this::filterXmlArticles, FOLLOW_LINKS);
		if (!dateFrom.equals(Instant.MIN)) {
			paths = paths
				.flatMap(this::handleParent)
				.distinct();
		}
		return paths.map(Readable::new);
	}

	private Stream<Path> handleParent(Path path) {
		Path parent = path.getParent();
		if (parent == null) {
			return Stream.of(path);
		}
		return Stream.iterate(parent, Objects::nonNull, Path::getParent)
			.map(p -> p.resolve(XML_FILENAME));
	}

	private boolean filterXmlArticles(Path path, BasicFileAttributes attrs) {
		return attrs.isRegularFile() && XML_FILENAME.equalsIgnoreCase(path.getFileName().toString()) && filterLatest(attrs);
	}

	private boolean filterLatest(BasicFileAttributes attrs) {
		return dateFrom.isBefore(attrs.lastModifiedTime().toInstant()) || dateFrom.isBefore(attrs.creationTime().toInstant());
	}

	@SneakyThrows
	public Writer writer(ArticleHeader header) {
		Path target = outputDir.resolve("." + header.getUrl());
		createDirsIfNeeded(target.getParent());
		return Files.newBufferedWriter(target, UTF_8, WRITE, TRUNCATE_EXISTING, CREATE);
	}

	public FileChannel inputStream(Category category, String src) {
		return inputStream(category.resolveWith(contentDir).resolve(src));
	}

	@SneakyThrows
	private FileChannel inputStream(Path path) {
		return FileChannel.open(path, READ);
	}

	@SneakyThrows
	public FileChannel outputStream(Category category, String src) {
		Path target = category.resolveWith(outputDir).resolve(src);
		createDirsIfNeeded(target.getParent());
		return FileChannel.open(target, WRITE, TRUNCATE_EXISTING, CREATE);
	}

	private void createDirsIfNeeded(Path dir) throws IOException {
		if (dir != null) {
			createDirectories(dir);
		}
	}

	@Override
	public void close() {
		// use for MVStore\ZIP implementation
	}

	@EqualsAndHashCode
	@RequiredArgsConstructor(access = PRIVATE)
	public final class Readable {

		private final Path path;

		public String[] getCategory() {
			return createCategory(contentDir.relativize(path).getParent());
		}

		private String[] createCategory(Path categoryPath) {
			if (categoryPath == null) {
				return new String[0];
			}
			String separator = path.getFileSystem().getSeparator();
			return categoryPath.toString().split(separator);
		}

		@SneakyThrows
		public Instant getLastModifiedTime() {
			return Files.getLastModifiedTime(path).toInstant();
		}

		public FileChannel readContent() {
			return inputStream(path);
		}

		@Override
		public String toString() {
			return path.toString();
		}

	}

}
