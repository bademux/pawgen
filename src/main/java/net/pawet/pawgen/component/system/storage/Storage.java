package net.pawet.pawgen.component.system.storage;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Category;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.Integer.MAX_VALUE;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.walk;
import static java.nio.file.StandardOpenOption.*;
import static java.util.Collections.reverseOrder;
import static java.util.Comparator.reverseOrder;
import static java.util.function.Predicate.not;
import static lombok.AccessLevel.PACKAGE;
import static net.pawet.pawgen.component.system.storage.Sha1DigestService.encode;

@Slf4j
@RequiredArgsConstructor(access = PACKAGE)
public class Storage {

	public static final String ARTICLE_FILENAME = "index.xml";

	private final Predicate<Path> isAttributeFile;
	private final Sha1DigestService digestService;
	private final Function<String, Optional<Path>> staticFileResolver;
	private final Path contentDir;
	private final Path outputDir;

	public static Storage create(@NonNull Function<String, Optional<Path>> staticFileResolver, @NonNull Path contentDir, @NonNull Path outputDir) {
		var metaService = new MetaService();
		var digestService = new Sha1DigestService(metaService);
		return new Storage(metaService::isAttributeFile, digestService, staticFileResolver, contentDir, outputDir);
	}

	@SneakyThrows
	public Stream<CategoryAwareResource> readChildren(String pathStr) {
		var directoryStream = Files.newDirectoryStream(contentDir.resolve(pathStr), Files::isDirectory);
		return StreamSupport.stream(directoryStream.spliterator(), false)
			.map(p -> p.resolve(ARTICLE_FILENAME))
			.filter(Files::exists)
			.filter(Files::isRegularFile)
			.map(p -> new CategoryAwareResource(Category.of(contentDir.relativize(p).getParent()), p, this))
			.onClose(() -> {
				try {
					directoryStream.close();
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
	}

	public SimpleResource resource(Path src, String dest) {
		return Optional.ofNullable(src)
			.filter(Files::exists)
			.filter(Files::isRegularFile)
			.map(path -> new SimpleResource(path, resolveOutputDir(dest), this))
			.orElseThrow();
	}

	public Optional<SimpleResource> resource(String rootRelativePath) {
		return Optional.ofNullable(rootRelativePath)
			.filter(not(String::isBlank))
			.map(this::resolveReadDir)
			.filter(Files::isRegularFile)
			.map(path -> new SimpleResource(path, resolveOutputDir(rootRelativePath), this));
	}

	public CategoryAwareResource resource(Category category) {
		return Optional.ofNullable(category)
			.map(Category::toString)
			.map(contentDir::resolve)
			.map(c -> c.resolve(ARTICLE_FILENAME))
			.filter(Files::exists)
			.filter(Files::isRegularFile)
			.map(path -> new CategoryAwareResource(category, path, this))
			.orElseThrow();
	}

	Path resolveReadDir(String pathStr) {
		if (pathStr.startsWith("/")) {
			String relPathStr = pathStr.substring(1);
			return staticFileResolver.apply(relPathStr)
				.orElseGet(() -> contentDir.resolve(relPathStr))
				.normalize();
		}
		return contentDir.resolve(pathStr).normalize();
	}

	@SneakyThrows
	InputStream read(Path path) {
		assert path.isAbsolute() : "expecting absolute path";
		return new BufferedInputStream(Files.newInputStream(path, READ));
	}

	public InputStream readFromInput(String relativeToRoot) {
		return read(resolveReadDir(relativeToRoot));
	}

	Path resolveOutputDir(String pathStr) {
		if (pathStr.length() > 1 && pathStr.startsWith("/")) {
			pathStr = pathStr.substring(1);
		}
		return outputDir.resolve(pathStr);
	}

	@SneakyThrows
	OutputStream write(Path dest) {
		assert dest.isAbsolute() : "expecting absolute path";
		return digestService.write(dest, this::newOutputStream);
	}

	@SneakyThrows
	private OutputStream newOutputStream(Path dest) {
		createDirsIfNeeded(dest.getParent());
		return new BufferedOutputStream(Files.newOutputStream(dest, WRITE, TRUNCATE_EXISTING, CREATE));
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
			return items.map(this::assertChecksum).reduce(Boolean::equals).orElse(true);
		}
	}

	private boolean assertChecksum(Path path) {
		String digest = encode(digestService.load(path)), calculated = encode(calculateSha1(path));
		if (digest.equals(calculated)) {
			return true;
		}
		log.error("Digest error: got {}, expected {} - {}", digest, calculated, path);
		return false;
	}

	@SneakyThrows
	private static byte[] calculateSha1(Path path) {
		try (var io = Files.newInputStream(path, READ)) {
			return Sha1DigestService.computeSha1(io);
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


