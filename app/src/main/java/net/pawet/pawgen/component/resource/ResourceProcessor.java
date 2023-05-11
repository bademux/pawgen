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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;

@Slf4j
@RequiredArgsConstructor
public final class ResourceProcessor {
	private final LongAccumulator imgProcessingCounter = new LongAccumulator(Long::sum, 0);
	private final LongAccumulator resProcessingCounter = new LongAccumulator(Long::sum, 0);
	private final Clock clock = Clock.systemUTC();

	private final Storage storage;
	private final ProcessableImageFactory processableImageFactory;
	private final Set<String> hosts;

	@SneakyThrows
	public Map<String, String> image(Category category, Map<String, String> attributes) {
		try {
			return ofNullable(attributes.get("src"))
				.map(this::handleLink)
				.map(category::resolve)
				.flatMap(storage::resource)
				.map(resource -> processableImageFactory.create(resource, attributes))
				.map(processable -> measured(processable, imgProcessingCounter::accumulate))
				.map(ResourceProcessor::safeApply)
				.orElse(attributes);
		} catch (Exception e) {
			log.warn("Error while processing tag 'image' in '{}'", category, e);
		}
		return attributes;
	}

	@SneakyThrows
	public Map<String, String> link(Category category, Map<String, String> attributes) {
		try {
			return ofNullable(attributes.get("href"))
				.map(this::handleLink)
				.map(category::resolve)
				.flatMap(storage::resource)
				.map(resource -> createProcessable(attributes, resource))
				.map(processable -> measured(processable, resProcessingCounter::accumulate))
				.map(ResourceProcessor::safeApply)
				.orElse(attributes);
		} catch (Exception e) {
			log.warn("Error while processing tag 'image' in '{}'", category, e);
		}
		return attributes;
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
