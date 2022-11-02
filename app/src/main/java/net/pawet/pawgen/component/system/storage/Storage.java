package net.pawet.pawgen.component.system.storage;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Category;

import java.io.*;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.lang.Integer.MAX_VALUE;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.walk;
import static java.nio.file.StandardOpenOption.*;
import static java.util.function.Predicate.not;
import static lombok.AccessLevel.PACKAGE;

@Slf4j
@RequiredArgsConstructor(access = PACKAGE)
public class Storage {

	public static final String ARTICLE_FILENAME = "index.xml";

	private final Map<Entry<Path, String>, Resource> resourceCache = new ConcurrentHashMap<>();
	private final Predicate<Path> isAttributeFile;
	private final Sha1DigestService digestService;
	private final Set<Path> staticDirs;
	private final Path contentDir;
	private final Path outputDir;

	public static Storage create(@NonNull Set<Path> staticDirs, @NonNull Path contentDir, @NonNull Path outputDir) {
		var metaService = new MetaService();
		var digestService = new Sha1DigestService(metaService);
		return new Storage(metaService::isAttributeFile, digestService, staticDirs, contentDir, outputDir);
	}

	@SneakyThrows
	public Stream<ArticleResource> readChildren(String pathStr) {
		return Files.list(contentDir.resolve(pathStr))
			.filter(Files::isDirectory)
			.map(p -> p.resolve(ARTICLE_FILENAME))
			.filter(Files::isRegularFile)
			.map(p -> new ArticleResource(Category.of(contentDir.relativize(p).getParent()), p, this));
	}

	public Optional<Resource> resource(String rootRelativePath) {
		return Optional.ofNullable(rootRelativePath)
			.filter(not(String::isBlank))
			.map(this::resolveInputDir)
			.flatMap(src -> resource(src, rootRelativePath));
	}

	public Optional<Resource> resource(Path src, String dest) {
		return Optional.ofNullable(src)
			.filter(Files::isRegularFile)
			.map(path -> createResource(path, dest));
	}

	private Resource createResource(Path src, String dest) {
		return resourceCache.computeIfAbsent(Map.entry(src, dest), path -> new SimpleResource(path.getKey(), resolveOutputDir(dest), this));
	}

	public ArticleResource categoryAwareResource(Category category) {
		return Optional.ofNullable(category)
			.map(Category::toString)
			.map(contentDir::resolve)
			.map(c -> c.resolve(ARTICLE_FILENAME))
			.filter(Files::isRegularFile)
			.map(path -> new ArticleResource(category, path, this))
			.orElseThrow();
	}

	public Stream<Resource> copyFiles() {
		return staticDirs.stream().flatMap(this::getPathStream);
	}

	private Stream<Resource> getPathStream(Path copyPath) {
		Path parent = copyPath.getParent();
		Path basePath = parent == null ? copyPath : parent;
		try {
			return Files.walk(copyPath, MAX_VALUE)
				.filter(Files::isRegularFile)
				.map(p -> resource(p, basePath.relativize(p).toString()))
				.filter(Optional::isPresent)
				.map(Optional::get);
		} catch (IOException e) {
			log.error("Can't find in basePath {}", copyPath, e);
		}
		return Stream.empty();
	}

	Path resolveInputDir(String pathStr) {
		if (!pathStr.startsWith("/")) {
			return contentDir.resolve(pathStr).normalize();
		}
		pathStr = pathStr.substring(1);
		for (Path staticDir : staticDirs) {
			Path targetPath = staticDir.getFileSystem().getPath(pathStr);
			Path staticDirName = staticDir.getFileName();
			if (targetPath.startsWith(staticDirName)) {
				return staticDir.resolve(staticDirName.relativize(targetPath));
			}
		}
		log.debug("looks like url {} is invalid, try to fix", pathStr);
		return contentDir.resolve(pathStr).normalize();
	}

	@SneakyThrows
	ReadableByteChannel read(Path path) {
		assert path.isAbsolute() : "expecting absolute path";
		return Files.newByteChannel(path, READ);
	}

	public ReadableByteChannel readFromInput(String relativeToRoot) {
		return read(resolveInputDir(relativeToRoot));
	}

	Path resolveOutputDir(String pathStr) {
		if (pathStr.length() > 1 && pathStr.startsWith("/")) {
			pathStr = pathStr.substring(1);
		}
		return outputDir.resolve(pathStr);
	}

	@SneakyThrows
	WritableByteChannel write(Path dest) {
		assert dest.isAbsolute() : "expecting absolute path";
		return digestService.write(dest, newWritableByteChannel(dest));
	}

	@SneakyThrows
	private WritableByteChannel newWritableByteChannel(Path dest) {
		createDirsIfNeeded(dest.getParent());
		return Files.newByteChannel(dest, WRITE, TRUNCATE_EXISTING, CREATE_NEW);
	}

	private static void createDirsIfNeeded(Path dir) throws IOException {
		if (dir != null && Files.notExists(dir)) {
			createDirectories(dir);
		}
	}

	public Stream<DigestAwareResource> readOutputDir() {
		return readOutputDirInternal().map(this::createDigestAwareData);
	}

	private DigestAwareResource createDigestAwareData(Path path) {
		return DigestAwareResource.of(digestService.load(path), path, outputDir.relativize(path), this);
	}

	public boolean cleanupOutputDir() {
		if (Files.notExists(outputDir)) {
			return false;
		}
		try (var files = walk(outputDir).sorted(Collections.reverseOrder())) {
			files.forEach(Storage::delete);
		} catch (IOException e) {
			log.error("can't cleanup operation for {}", outputDir, e);
			return false;
		}
		return true;
	}

	@SneakyThrows
	private static void delete(Path path) {
		if (!path.equals(path.getRoot())) { // test is root '/' eg. for zip:// files
			Files.delete(path);
		}
	}

	@SneakyThrows
	private Stream<Path> readOutputDirInternal() {
		return Files.find(outputDir, MAX_VALUE, this::filterOutputDir);
	}

	@SneakyThrows
	private boolean filterOutputDir(Path path, BasicFileAttributes basicFileAttributes) {
		return basicFileAttributes.isRegularFile() && !Files.isHidden(path) && !isAttributeFile.test(path);
	}

	public boolean assertChecksums() {
		log.info("Checking sums");
		try (Stream<Path> items = readOutputDirInternal()) {
			return items.map(digestService::assertChecksum).reduce(Boolean::equals).orElse(true);
		}
	}

	Instant getModificationDate(Path file) {
		try {
			return Files.getLastModifiedTime(file).toInstant();
		} catch (IOException e) {
			return Instant.MIN;
		}
	}

}


