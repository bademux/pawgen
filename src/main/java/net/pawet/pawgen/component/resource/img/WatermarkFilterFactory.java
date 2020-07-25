package net.pawet.pawgen.component.resource.img;

import lombok.RequiredArgsConstructor;
import net.pawet.pawgen.component.system.storage.Storage;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.function.Consumer;

import static java.util.function.Predicate.not;

@RequiredArgsConstructor(staticName = "of")
public final class WatermarkFilterFactory {

	private final Storage storage;

	public Consumer<BufferedImage> create(String watermarkText) {
		try (var is = storage.readWatermarkFile()) {
			return WatermarkFilter.of(is);
		} catch (Exception ignore) {
		}
		return Optional.ofNullable(watermarkText)
			.filter(not(String::isBlank))
			.map(WatermarkFilter::of)
			.orElseGet(WatermarkFilter::of);

	}

}
