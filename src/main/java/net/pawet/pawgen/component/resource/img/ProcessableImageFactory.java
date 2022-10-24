package net.pawet.pawgen.component.resource.img;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.system.storage.ImageResource;

import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor(staticName = "of")
public final class ProcessableImageFactory {

	private final LongAccumulator processingCounter = new LongAccumulator(Long::sum, 0);
	private final Clock clock;
	private final Consumer<BufferedImage> watermarkFilter;

	public Supplier<Map<String, String>> create(Map<String, String> attributes, Category category, ImageResource img) {
		var imageWithThumbnailProcessable = new ImageWithThumbnailProcessable(img, attributes, watermarkFilter, category);
		return () -> {
			long start = clock.millis();
			var result = imageWithThumbnailProcessable.get();
			processingCounter.accumulate(clock.millis() - start);
			return result;
		};
	}

	public Duration getProcessingTime(){
		return Duration.ofMillis(processingCounter.get());
	}

}
