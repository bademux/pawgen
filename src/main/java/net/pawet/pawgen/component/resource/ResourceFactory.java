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
				.map(this::handleLink)
				.map(category::resolve)
				.map(this::imageResource)
				.map(res -> processableImageFactory.create(attributes, category, res));
			case "a" -> Optional.ofNullable(attributes.get("href"))
				.map(this::handleLink)
				.map(category::resolve)
				.map(storage::resource)
				.map(resource -> new Processable(resource, attributes));
			default -> Optional.empty();
		};
	}

	private ImageResource imageResource(String rootRelativePath) {
		return ImageResource.of(storage.resource(rootRelativePath), rootRelativePath);
	}

	String handleLink(String urlStr) {
		try {
			var uri = new URI(urlStr);
			if (hosts.contains(uri.getHost()) || uri.getScheme() == null) { //is current host or  relative to host
				String path = uri.getPath();
				if (path != null && !path.isBlank()) {
					return path;
				}
			}
		} catch (URISyntaxException ignore) {
		}
		return null;
	}

	public void createAttachmentResource(String src) {
		Processable.noAttributes(storage.resource(src));
	}

}
