package net.pawet.pawgen.component.system.storage;

import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.lang.Integer.MAX_VALUE;
import static java.nio.file.FileVisitOption.FOLLOW_LINKS;
import static java.util.Map.entry;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static lombok.AccessLevel.PACKAGE;

@Slf4j
public class StaticFileService {

	@NonNull
	private final Collection<URI> staticDirs;
	@NonNull
	private final Path outputDir;
	@NonNull
	private final FileSystemRegistry fsRegistry;
	private final BiPredicate<Instant, Instant> isNewOrUpdated;
	@Getter(lazy = true, value = PACKAGE)
	private final Set<Map.Entry<Path, Path>> staticFiles = createStaticFiles();

	public StaticFileService(@NonNull Collection<URI> staticDirs, @NonNull Path outputDir, @NonNull FileSystemRegistry fsRegistry, @NonNull BiPredicate<Instant, Instant> isNewOrUpdated) {
		this.staticDirs = staticDirs;
		this.outputDir = outputDir;
		this.fsRegistry = fsRegistry;
		this.isNewOrUpdated = isNewOrUpdated;
	}

	private Set<Map.Entry<Path, Path>> createStaticFiles() {
		assert staticDirs != null;
		try (var stream = staticDirs.stream()) {
			return stream.flatMap(this::createStaticFileCopySpec).collect(toUnmodifiableSet());
		}
	}

	public void copyStaticResources(Function<Path, OutputStream> writer) {
		getStaticFiles().stream()
			.filter(entry -> Files.notExists(entry.getValue()))
			.forEach(entry -> copyFile(entry.getKey(), () -> writer.apply(entry.getValue())));
	}

	private void copyFile(Path src, Supplier<OutputStream> dest) {
		log.debug("Copy to: {} from {}", dest, src);
		try (var os = dest.get()) {
			Files.copy(src, os);
		} catch (FileAlreadyExistsException e) {
			log.debug("Already processed: {}", e.getFile());
		} catch (IOException e) {
			log.error("Can't copy file '{}' to '{}'", src, dest, e);
		}
	}

	@SneakyThrows
	private Stream<Map.Entry<Path, Path>> createStaticFileCopySpec(URI uri) {
		String ssp = uri.getSchemeSpecificPart();
		boolean idDirContent = ssp != null && ssp.endsWith("**");
		if (idDirContent) {
			uri = new URI(uri.getScheme(), ssp.substring(0, ssp.length() - "**".length()), uri.getFragment());
		}
		var path = fsRegistry.getPathFsRegistration(uri);
		if (Files.isRegularFile(path)) {
			return Stream.of(entry(path, outputDir.resolve(path.getFileName().toString())));
		}
		Path basePath = idDirContent ? path : path.getParent() == null ? path : path.getParent();
		return Files.find(path, MAX_VALUE, (p, attrs) -> attrs.isRegularFile() && isNewOrUpdated.test(attrs.lastModifiedTime().toInstant(), attrs.creationTime().toInstant()), FOLLOW_LINKS)
			.map(p -> entry(p, outputDir.resolve(basePath.relativize(p).toString())));
	}

	public Optional<Path> resolve(String relPath) {
		return getStaticFiles().stream().map(Map.Entry::getKey)
			.filter(path -> path.endsWith(relPath))
			.findAny()
			.map(path -> path.resolve(relPath));
	}
}
