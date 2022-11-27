package net.pawet.pawgen.component.system.storage;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Category;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.lang.Integer.MAX_VALUE;
import static java.nio.channels.Channels.newWriter;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.*;
import static java.nio.file.StandardOpenOption.*;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.*;
import static lombok.AccessLevel.PACKAGE;

@Slf4j
@RequiredArgsConstructor(access = PACKAGE)
public class Storage {

	public static final String ARTICLE_FILENAME_PREFIX = "index.";
	public static final String ARTICLE_FILENAME_SUFFIX = ".xml";
	public static final String REDIRECTS_FILE = "_redirects";

	private final Map<Entry<Path, String>, Resource> resourceCache = new ConcurrentHashMap<>();
	private final Predicate<Path> isAttributeFile;
	private final Sha1DigestService digestService;
	private final Map<String, Path> staticFiles;
	private final Path contentDir;
	private final Path outputDir;

	public static Storage create(Stream<Entry<Path, Path>> relativePathPerPath, @NonNull Path contentDir, @NonNull Path outputDir) {
		var metaService = new MetaService();
		var digestService = new Sha1DigestService(metaService);
		try (relativePathPerPath) {
			var staticFileMap = relativePathPerPath.collect(toMap(e -> asRelativeUri(e.getKey()), Entry::getValue, (relativePath, __) -> {
				throw new IllegalArgumentException("Multiple static files in static dir for" + relativePath);
			}));
			return new Storage(metaService::isAttributeFile, digestService, staticFileMap, contentDir, outputDir);
		}
	}

	private static String asRelativeUri(Path relValue) {
		var sj = new StringJoiner("/");
		for (Path path : relValue) {
			sj.add(path.toString());
		}
		return sj.toString();
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

	@SneakyThrows
	public Stream<ArticleResource> readChildren(Category category) {
		return Files.list(contentDir.resolve(category.toString()))
			.filter(Files::isDirectory)
			.flatMap(Storage::listArticlesInDir)
			.map(p -> new ArticleResource(Category.of(contentDir.relativize(p).getParent()), p, this));
	}

	public Stream<ArticleResource> read(Category category) {
		return Optional.ofNullable(category)
			.map(Category::toString)
			.map(contentDir::resolve)
			.stream()
			.flatMap(Storage::listArticlesInDir)
			.map(path -> new ArticleResource(category, path, this));
	}

	@SneakyThrows
	private static Stream<Path> listArticlesInDir(Path c) {
		return Files.list(c)
			.filter(Files::isRegularFile)
			.filter(path -> {
				String name = path.getFileName().toString();
				return name.startsWith(ARTICLE_FILENAME_PREFIX) && name.endsWith(ARTICLE_FILENAME_SUFFIX);
			});
	}

	public Stream<Resource> staticFiles() {
		return staticFiles.entrySet().stream()
			.filter(e -> !REDIRECTS_FILE.equalsIgnoreCase(e.getKey()))
			.map(e -> createResource(e.getValue(), e.getKey()));
	}

	Path resolveInputDir(String pathStr) {
		if (!pathStr.startsWith("/")) {
			return contentDir.resolve(pathStr).normalize();
		}
		pathStr = pathStr.substring(1);
		Path staticFile = staticFiles.get(pathStr);
		if (staticFile != null) {
			return staticFile;
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
		if (dir != null && notExists(dir)) {
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
		if (notExists(outputDir)) {
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

	@SneakyThrows
	public void writeAliases(List<Entry<String, String>> aliases) {
		Path destRedirects = outputDir.resolve(REDIRECTS_FILE);
		var userDefinedRedirects = staticFiles.get(REDIRECTS_FILE);
		try (var writer = newWriter(write(destRedirects), UTF_8)) {
			if (userDefinedRedirects != null) {
				writeExistingFile(userDefinedRedirects, writer);
			}
			writer.append("#Autogenerated redirectsFile").append('\n');
			for (Entry<String, String> alias : aliases) {
				writer.append(URI.create(alias.getKey()).toASCIIString()).append(' ').append(URI.create(alias.getValue()).toASCIIString()).append('\n');
			}
			writer.append('\n');
		}
	}

	private static void writeExistingFile(Path redirectsFile, Writer writer) {
		try (var reader = Files.newBufferedReader(redirectsFile)) {
			reader.transferTo(writer);
			writer.append('\n');
		} catch (FileNotFoundException e) {
			log.trace("No _redirects", e);
		} catch (Exception e) {
			log.error("Cant process existing _redirects file");
		}
	}

}


