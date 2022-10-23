package net.pawet.pawgen.component.resource;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.resource.img.ProcessableImageFactory;
import net.pawet.pawgen.component.system.storage.ImageResource;
import net.pawet.pawgen.component.system.storage.Storage;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@Slf4j
public record ResourceFactory(Storage storage,
							  ProcessableImageFactory processableImageFactory,
							  Set<String> hosts) {

	@SneakyThrows
	public Map<String, String> createResource(String tagName, Category category, Map<String, String> attributes) {
		try {
			return create(tagName, category, attributes)
				.map(Supplier::get)
				.orElse(attributes);
		} catch (Exception e) {
			log.warn("Error while processing tag '{}' in '{}'", tagName, category);
		}
		return attributes;
	}

	private Optional<Supplier<Map<String, String>>> create(String tagName, Category category, Map<String, String> attributes) {
		return switch (tagName) {
			case "img" -> Optional.ofNullable(attributes.get("src"))
				.flatMap(this::handleLink)
				.map(category::resolve)
				.flatMap(this::imageResource)
				.map(res -> processableImageFactory.create(attributes, category, res));
			case "a" -> Optional.ofNullable(attributes.get("href"))
				.flatMap(this::handleLink)
				.map(category::resolve)
				.flatMap(storage::resource)
				.map(resource -> new Processable(resource, attributes));
			default -> Optional.empty();
		};
	}

	private Optional<ImageResource> imageResource(String rootRelativePath) {
		return storage.resource(rootRelativePath).map(simpleResource -> ImageResource.of(simpleResource, rootRelativePath));
	}

	Optional<String> handleLink(String urlStr) {
		try {
			var uri = new URI(urlStr);
			if (hosts.contains(uri.getHost()) || uri.getScheme() == null) { //is current host or relative to host
				String path = uri.getPath();
				if (path != null && !path.isBlank()) {
					return Optional.of(path);
				}
			}
		} catch (URISyntaxException ignore) {
		}
		return Optional.empty();
	}

}
