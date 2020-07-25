package net.pawet.pawgen.component.system.storage;

import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.Integer.MAX_VALUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.walk;
import static java.nio.file.StandardOpenOption.*;
import static java.util.Collections.reverseOrder;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static lombok.AccessLevel.PACKAGE;
import static net.pawet.pawgen.component.system.storage.Sha1DigestService.encode;

@Slf4j
@RequiredArgsConstructor(access = PACKAGE)
public abstract class Storage implements AutoCloseable {

	public static final String ARTICLE_FILENAME = "index.xml";

	private final Predicate<Path> isAttributeFile;
	private final Sha1DigestService digestService;
	private final LastBuild lastBuild;
	private final StaticFileService staticFileService;
	private final Path contentDir;
	private final Path outputDir;
	private final Path templatesDir;
	private final Path watermarkFile;

	@Builder
	private static Storage create(@NonNull URI contentUri, @NonNull URI outputUri, @NonNull Collection<URI> staticUris, @NonNull URI templatesUri, URI watermarkUri, Instant dateFrom) {
		var fsRegistry = new FileSystemRegistry();
		var contentDir = fsRegistry.getPathFsRegistration(contentUri);
		var outputDir = fsRegistry.getPathFsRegistration(outputUri);
		var templatesDir = fsRegistry.getPathFsRegistration(templatesUri);
		var metaService = new MetaService();
		var lastBuildService = LastBuild.of(dateFrom, outputDir);
		var filter = ((Predicate<BasicFileAttributes>) BasicFileAttributes::isRegularFile)
			.and((attrs) -> lastBuildService.isNewOrUpdated(attrs.lastModifiedTime().toInstant(), attrs.creationTime().toInstant()));
		var staticFileService = new StaticFileService(staticUris, outputDir, fsRegistry, filter);
		var digestService = new Sha1DigestService(metaService);
		var watermarkFile = watermarkUri == null ? null : fsRegistry.getPathFsRegistration(watermarkUri);
		return new Storage(metaService::isAttributeFile, digestService, lastBuildService, staticFileService, contentDir, outputDir, templatesDir, watermarkFile) {
			@Override
			public void close() {
				fsRegistry.close();
			}
		};
	}

	@SneakyThrows
	public Reader resolveTemplate(String name) {
		return Files.newBufferedReader(templatesDir.resolve(name), UTF_8);
	}

	public InputStream readWatermarkFile() throws IOException {
		if (watermarkFile == null) {
			throw new IllegalStateException("no watermarkFile");
		}
		return new BufferedInputStream(Files.newInputStream(watermarkFile, READ));
	}

	public void copyStaticResources() {
		staticFileService.getStaticFiles()
			.forEach(entry -> copyFile(entry.getKey(), entry.getValue()));
	}

	private void copyFile(Path src, Path dest) {
		if (Files.exists(dest)) {
			log.debug("Already exists: {}, skiping src: {}", dest, src);
			return;
		}
		log.debug("Copy to: {} from {}", dest, src);
		try (var os = write(dest)) {
			createDirsIfNeeded(dest.getParent());
			Files.copy(src, os); // use #write to calculate digest while copying
		} catch (FileAlreadyExistsException e) {
			log.debug("Already processed: {}", e.getFile());
		} catch (IOException e) {
			log.error("Can't copy file '{}' to '{}'", src, dest, e);
		}
	}

	@SneakyThrows
	public CategoryAwareResource readArticleByCategory(String pathStr) {
		Path path = contentDir.resolve(pathStr).resolve(ARTICLE_FILENAME);
		var attrs = requireNonNull(readBasicAttributes(path), "Can't read file attributes for: " + path);
		if (!attrs.isRegularFile()) {
			throw new IllegalStateException("Root path is not the file: " + path);
		}
		return CategoryAwareResource.of(contentDir.relativize(path).getParent(), Resource.fromSrc(path, this));
	}

	@SneakyThrows
	public Stream<CategoryAwareResource> readChildren(String pathStr) {
		return StreamSupport.stream(Files.newDirectoryStream(contentDir.resolve(pathStr), Files::isDirectory).spliterator(), false)
			.map(p -> p.resolve(ARTICLE_FILENAME))
			.filter(p -> {
				var attrs = readBasicAttributes(p);
				return attrs != null && attrs.isRegularFile();
			})
			.map(p -> CategoryAwareResource.of(contentDir.relativize(p).getParent(), Resource.fromSrc(p, this)));

	}

	private static BasicFileAttributes readBasicAttributes(Path path) {
		try {
			return Files.getFileAttributeView(path, BasicFileAttributeView.class).readAttributes();
		} catch (IOException ignore) {
			return null;
		}
	}

	public InputStream read(String src) {
		return read(resolveReadDir(src).normalize());
	}

	public Optional<Resource> resource(String rootRelativePath) {
		return Optional.ofNullable(rootRelativePath)
			.filter(not(String::isBlank))
			.map(this::resolveReadDir)
			.filter(Files::isRegularFile)
			.map(Path::normalize)
			.map(path -> Resource.from(path, resolveOutputDir(rootRelativePath), rootRelativePath, this));
	}

	Path resolveReadDir(String pathStr) {
		if (pathStr.startsWith("/")) {
			return staticFileService.resolve(pathStr.substring(1))
				.orElseGet(() -> contentDir.resolve(pathStr));
		}
		return contentDir.resolve(pathStr);
	}

	@SneakyThrows
	InputStream read(Path path) {
		assert path.isAbsolute() : "expecting absolute path";
		return new BufferedInputStream(Files.newInputStream(path, READ));
	}

	public OutputStream write(String dest) {
		return write(resolveOutputDir(dest).normalize());
	}

	public Path resolveOutputDir(String pathStr) {
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
		return readOutputDirInternal().map(this::createDigestAwareResource);
	}

	private DigestAwareResource createDigestAwareResource(Path path) {
		Path relPath = outputDir.relativize(path);
		Resource resource = Resource.fromSrcWithRelPath(path, relPath, this);
		return DigestAwareResource.of(digestService.load(path), resource);
	}

	@SneakyThrows
	public boolean cleanupOutDirIfNeeded() {
		if (Files.exists(outputDir)) {
			walk(outputDir).sorted(reverseOrder()).forEach(Storage::delete);
			return true;
		}
		return false;
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

	@SneakyThrows
	public boolean isNewOrChanged(String category) {
		Path path = resolveReadDir(category).resolve(ARTICLE_FILENAME).normalize();
		var attrs = Files.getFileAttributeView(path, BasicFileAttributeView.class).readAttributes();
		return lastBuild.isNewOrUpdated(attrs.lastModifiedTime().toInstant(), attrs.creationTime().toInstant());
	}

	public void timestamp() {
		lastBuild.timestamp();
	}

}


