package net.pawet.pawgen.component.resource.img;

import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.system.storage.FileSystemRegistry;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

import static java.nio.file.Files.newInputStream;
import static java.nio.file.StandardOpenOption.READ;
import static java.util.function.Predicate.not;

@Slf4j
public record WatermarkFilterFactory(FileSystemRegistry fsRegistry) {

	public Consumer<BufferedImage> create(String watermarkText, URI watermarkFile) {
		if (watermarkFile != null) {
			try (var is = readWatermarkFile(fsRegistry.getPathFsRegistration(watermarkFile))) {
				return WatermarkFilter.of(is);
			} catch (Exception e) {
				log.error("Can't read {}", watermarkFile);
			}
		}
		return Optional.ofNullable(watermarkText)
			.filter(not(String::isBlank))
			.map(WatermarkFilter::of)
			.orElseGet(WatermarkFilter::of);

	}

	private InputStream readWatermarkFile(Path watermarkFile) throws IOException {
		return new BufferedInputStream(newInputStream(watermarkFile, READ));
	}

}
