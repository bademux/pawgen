package net.pawet.pawgen.component.resource;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.system.storage.ReadableResource;
import net.pawet.pawgen.component.system.storage.Resource;
import net.pawet.pawgen.component.system.storage.WritableResource;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Processable<T extends WritableResource & ReadableResource> implements Supplier<Map<String, String>> {

	@EqualsAndHashCode.Include
	protected final T resource;

	@ToString.Include
	protected final Map<String, String> attributes;

	public static Processable<?> noAttributes(Resource resource) {
		return new Processable<>(resource, Map.of());
	}

	public Processable(T resource, Map<String, String> attributes) {
		this.resource = resource;
		this.attributes = attributes;
	}

	@Override
	public Map<String, String> get() {
		try (var is = resource.inputStream(); var os = resource.outputStream()) {
			is.transferTo(os);
		} catch (FileAlreadyExistsException e) {
			log.debug("Already processed: {}", e.getFile());
		} catch (NoSuchFileException e) {
			log.trace("No file found: {}", e.getFile());
		} catch (Exception e) {
			log.error("exception while saving thumbnail", e);
		}
		return attributes;
	}


}
