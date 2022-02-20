package net.pawet.pawgen.component.system.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Optional;

import static lombok.AccessLevel.PACKAGE;

@Slf4j
@RequiredArgsConstructor(access = PACKAGE)
public class LastBuildService {

	private final Instant dateFrom;
	private final Path outputDir;

	public static LastBuildService create(Instant dateFrom, Path outputDir) {
		dateFrom = Optional.ofNullable(dateFrom)
			.or(() -> getLastUpdateRootFile(outputDir))
			.orElse(Instant.MIN);
		return new LastBuildService(dateFrom, outputDir);
	}

	private static Optional<Instant> getLastUpdateRootFile(Path outputDir) {
		try {
			if (Files.list(outputDir).findAny().isPresent()) {
				return Optional.ofNullable(Files.getLastModifiedTime(outputDir).toInstant());
			}
		} catch (IOException ignore) {
		}
		return Optional.empty();
	}

	public void timestamp() {
		try {
			Files.setLastModifiedTime(outputDir, FileTime.from(Instant.now()));
		} catch (IOException ignore) {
		}
	}

	public boolean isNewOrUpdated(Instant lastModified, Instant creation) {
		return dateFrom.isBefore(lastModified) || dateFrom.isBefore(creation);
	}

}

