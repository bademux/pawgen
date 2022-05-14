package net.pawet.pawgen.component.system.storage;

import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
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
	private final FileSystemRegistry fsRegistry;
	@Getter(lazy = true, value = PACKAGE)
	private final Set<Entry<Path, String>> staticFiles = createStaticFiles();

	public StaticFileService(@NonNull Collection<URI> staticDirs, @NonNull FileSystemRegistry fsRegistry) {
		this.staticDirs = staticDirs;
		this.fsRegistry = fsRegistry;
	}

	private Set<Entry<Path, String>> createStaticFiles() {
		assert staticDirs != null;
		try (var stream = staticDirs.stream()) {
			return stream.flatMap(this::createStaticFileCopySpec).collect(toUnmodifiableSet());
		}
	}

	public Stream<SimpleResource> copyStaticResources(BiFunction<Path, String, SimpleResource> resourceCreator) {
		return getStaticFiles().stream().map(entry -> resourceCreator.apply(entry.getKey(), entry.getValue()));
	}
	@SneakyThrows
	private Stream<Entry<Path, String>> createStaticFileCopySpec(URI uri) {
		String ssp = uri.getSchemeSpecificPart();
		boolean isDirContent = ssp != null && ssp.endsWith("**");
		if (isDirContent) {
			uri = new URI(uri.getScheme(), ssp.substring(0, ssp.length() - "**".length()), uri.getFragment());
		}
		var path = fsRegistry.getPathFsRegistration(uri);
		if (Files.isRegularFile(path)) {
			return Stream.of(entry(path, path.getFileName().toString()));
		}
		Path basePath = isDirContent ? path : path.getParent() == null ? path : path.getParent();
		return Files.find(path, MAX_VALUE, (p, attrs) -> attrs.isRegularFile(), FOLLOW_LINKS)
			.map(p -> entry(p, basePath.relativize(p).toString()));
	}

	public Optional<Path> resolve(String relPath) {
		return getStaticFiles().stream().map(Entry::getKey)
			.filter(path -> path.endsWith(relPath))
			.findAny()
			.map(path -> path.resolve(relPath));
	}
}
