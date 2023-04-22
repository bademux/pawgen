package net.pawet.pawgen.component.resource;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.resource.img.ProcessableImageFactory;
import net.pawet.pawgen.component.system.storage.Resource;
import net.pawet.pawgen.component.system.storage.Storage;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.NoSuchFileException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public final class ResourceProcessor implements Function<ResourceProcessor.ProcessingItem, Map<String, String>> {
	private final LongAccumulator imgProcessingCounter = new LongAccumulator(Long::sum, 0);
	private final LongAccumulator resProcessingCounter = new LongAccumulator(Long::sum, 0);
	private final Clock clock = Clock.systemUTC();

	private final Storage storage;
	private final ProcessableImageFactory processableImageFactory;
	private final Set<String> hosts;


	public record ProcessingItem(String tagName, Category category, @Delegate Map<String, String> attributes) implements Map<String, String> {
	}

	@SneakyThrows
	@Override
	public Map<String, String> apply(ProcessingItem processingItem) {
		try {
			return create(processingItem.tagName, processingItem.category, processingItem)
				.map(ResourceProcessor::safeApply)
				.orElse(processingItem);
		} catch (Exception e) {
			log.warn("Error while processing tag '{}' in '{}'", processingItem.tagName, processingItem.category, e);
		}
		return processingItem;
	}

	private static Map<String, String> safeApply(Supplier<Map<String, String>> resource) {
		try {
			return resource.get();
		} catch (Exception e) {
			if (e instanceof NoSuchFileException) {
				log.debug("No file found: {}", ((NoSuchFileException) e).getFile());
			} else {
				log.error("exception while saving thumbnail", e);
			}
		}
		return null;
	}

	private Optional<Supplier<Map<String, String>>> create(String tagName, Category category, Map<String, String> attributes) {
		return switch (tagName) {
			case "img" -> Optional.ofNullable(attributes.get("src"))
				.map(this::handleLink)
				.map(category::resolve)
				.flatMap(storage::resource)
				.map(resource -> processableImageFactory.create(resource, attributes))
				.map(processable -> measured(processable, imgProcessingCounter::accumulate));
			case "a" -> Optional.ofNullable(attributes.get("href"))
				.map(this::handleLink)
				.map(category::resolve)
				.flatMap(storage::resource)
				.map(resource -> createProcessable(attributes, resource))
				.map(processable -> measured(processable, resProcessingCounter::accumulate));
			default -> Optional.empty();
		};
	}

	private Supplier<Map<String, String>> measured(Supplier<Map<String, String>> processable, LongConsumer accumulator) {
		return () -> {
			long start = clock.millis();
			var result = processable.get();
			accumulator.accept(clock.millis() - start);
			return result;
		};
	}

	private static Supplier<Map<String, String>> createProcessable(Map<String, String> attributes, Resource resource) {
		return () -> {
			resource.transfer();
			return attributes;
		};
	}

	public Duration getImageProcessingTime() {
		return Duration.ofMillis(imgProcessingCounter.get());
	}

	public Duration getResourceProcessingTime() {
		return Duration.ofMillis(resProcessingCounter.get());
	}

	String handleLink(String urlStr) {
		try {
			var uri = new URI(urlStr);
			if (hosts.contains(uri.getHost()) || uri.getScheme() == null) { //is current host or relative to host
				String path = uri.getPath();
				if (path != null && !path.isBlank()) {
					return path;
				}
			}
		} catch (URISyntaxException ignore) {
		}
		return null;
	}

}
