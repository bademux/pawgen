package net.pawet.pawgen.component.system.storage;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.lang.Integer.MAX_VALUE;
import static java.nio.file.FileVisitOption.FOLLOW_LINKS;
import static java.util.Map.entry;
import static java.util.stream.Collectors.toUnmodifiableSet;

@RequiredArgsConstructor
class StaticFileService {

	@NonNull
	private final Collection<URI> staticDirs;
	@NonNull
	private final Path outputDir;
	@NonNull
	private final FileSystemRegistry fsRegistry;
	@NonNull
	private final Predicate<BasicFileAttributes> filterLatestFiles;
	@Getter(lazy = true)
	private final Set<Map.Entry<Path, Path>> staticFiles = createStaticFiles();

	private Set<Map.Entry<Path, Path>> createStaticFiles() {
		try (var stream = staticDirs.stream()) {
			return stream.flatMap(this::createStaticFileCopySpec).collect(toUnmodifiableSet());
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
		return Files.find(path, MAX_VALUE, (p, attributes) -> filterLatestFiles.test(attributes), FOLLOW_LINKS)
			.map(p -> entry(p, outputDir.resolve(basePath.relativize(p).toString())));
	}

	public Optional<Path> resolve(String relPath) {
		return getStaticFiles().stream().map(Map.Entry::getKey)
			.filter(path -> path.endsWith(relPath))
			.findAny()
			.map(path -> path.resolve(relPath));
	}
}
