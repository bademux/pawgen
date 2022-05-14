package net.pawet.pawgen.component.resource;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.system.storage.Resource;

import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
public class Processable implements Supplier<Map<String, String>> {

	@EqualsAndHashCode.Include
	private final Resource resource;

	@ToString.Include
	private final Map<String, String> attributes;

	public static Processable noAttributes(Resource resource) {
		return new Processable(resource, Map.of());
	}

	@Override
	public Map<String, String> get() {
		transferSilently(resource);
		return attributes;
	}

	public static void transferSilently(Resource resource) {
		try {
			resource.transfer();
		} catch (UncheckedIOException e) {
			if (e.getCause() instanceof FileAlreadyExistsException) {
				log.debug("Already processed: {}", ((FileAlreadyExistsException) e.getCause()).getFile());
			} else if (e.getCause() instanceof NoSuchFileException) {
				log.debug("No file found: {}", ((FileAlreadyExistsException) e.getCause()).getFile());
			}
		} catch (Exception e) {
			log.error("exception while saving thumbnail", e);
		}
	}


}
