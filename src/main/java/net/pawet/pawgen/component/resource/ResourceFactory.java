package net.pawet.pawgen.component.resource;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.resource.img.ImageFactory;
import net.pawet.pawgen.component.system.storage.Storage;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor(staticName = "of")
public final class ResourceFactory {

	private final Storage storage;
	private final ImageFactory imageFactory;
	private final Set<String> hosts;

	@SneakyThrows
	public Map<String, String> createResource(String name, Category category, Map<String, String> attributes) {
		try {
			return create(name, category, attributes)
				.map(Processable::get)
				.orElse(attributes);
		} catch (Exception e) {
			log.warn("Error while processing file '{}' in '{}'", name, category);
		}
		return attributes;
	}

	private Optional<Processable<?>> create(String name, Category category, Map<String, String> attributes) {
		switch (name) {
			case "img":
				return Optional.ofNullable(attributes.get("src"))
					.map(this::handleLink)
					.map(category::resolve)
					.flatMap(storage::resource)
					.map(resource -> imageFactory.create(attributes, category, resource));
			case "a":
				return Optional.ofNullable(attributes.get("href"))
					.map(this::handleLink)
					.map(category::resolve)
					.flatMap(storage::resource)
					.map(resource -> new Processable<>(resource, attributes));
			default:
				return Optional.empty();
		}
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
		storage.resource(src).map(Processable::noAttributes)
			.ifPresent(Processable::get);
	}

}
