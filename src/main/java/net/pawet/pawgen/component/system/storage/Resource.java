package net.pawet.pawgen.component.system.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;

public interface Resource extends ReadableResource, WritableResource {

	InputStream inputStream();

	OutputStream outputStream();

	default void transfer() {
		try (var is = inputStream(); var os = outputStream()) {
			is.transferTo(os);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
