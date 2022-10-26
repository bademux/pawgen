package net.pawet.pawgen.component.system.storage;

import lombok.SneakyThrows;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;

import static org.slf4j.LoggerFactory.getLogger;

public interface Resource extends ReadableResource, WritableResource {

	InputStream inputStream();

	OutputStream outputStream();

	@SneakyThrows
	default void transfer() {
		try (var is = inputStream(); var os = outputStream()) {
			is.transferTo(os);
		} catch (FileAlreadyExistsException e) {
			getLogger(Resource.class).trace("Already transferred: {}", ((FileAlreadyExistsException) e.getCause()).getFile());
		}
	}

	Resource EMPTY = new Resource() {
		@Override
		public InputStream inputStream() {
			return InputStream.nullInputStream();
		}

		@Override
		public OutputStream outputStream() {
			return OutputStream.nullOutputStream();
		}
	};

}
