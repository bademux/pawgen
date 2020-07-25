package net.pawet.pawgen.component.system.storage;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.Files.getFileAttributeView;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Optional.ofNullable;

@Slf4j
final class MetaService {

	private final Map<Entry<Path, String>, byte[]> data = new ConcurrentHashMap<>();

	@SneakyThrows
	public void store(Path target, String key, byte[] value) {
		data.put(Map.entry(target, key), value);
		log.trace("Store to file attribute '{}' for {}", target, key);
		var attributeView = getFileAttributeView(target, UserDefinedFileAttributeView.class);
		if (attributeView != null) {
			try {
				attributeView.write(key, ByteBuffer.wrap(value));
				return;
			} catch (FileSystemException e) {
				log.trace("Can't write {} file attribute for {}", key, target, e);
			}
		}
		Path metaFile = resolveMetaFile(target, key);
		log.trace("Or create value file {}", metaFile);
		Files.write(metaFile, value, TRUNCATE_EXISTING, CREATE);
	}

	@SneakyThrows
	public Optional<byte[]> load(Path target, String key) {
		return Optional.of(Map.entry(target, key))
			.map(data::get)
			.or(() -> readAttribute(target, key))
			.or(() -> readAttrFromFile(target, key));
	}

	private Optional<byte[]> readAttrFromFile(Path target, String key) {
		log.trace("Or try value file {}", target);
		Path path = resolveMetaFile(target, key);
		if (Files.notExists(path)) {
			return Optional.empty();
		}
		try {
			return Optional.of(Files.readAllBytes(path));
		} catch (IOException e) {
			log.trace("Can't write file {}.{}", target, key, e);
		}
		return Optional.empty();
	}

	private Optional<byte[]> readAttribute(Path target, String key) {
		log.trace("Read from attribute first {}", target);
		if (!target.getFileSystem().supportedFileAttributeViews().contains("user")) {
			return Optional.empty();
		}
		var attributeView = getFileAttributeView(target, UserDefinedFileAttributeView.class);
		try {
			ByteBuffer digestBuff = ByteBuffer.allocate(attributeView.size(key));
			attributeView.read(key, digestBuff);
			return Optional.of(digestBuff.array());
		} catch (IOException e) {
			log.trace("Can't read '{}' attribute for {}", key, target, e);
		}
		return Optional.empty();
	}

	private static Path resolveMetaFile(Path target, String key) {
		if (Files.isDirectory(target)) {
			return target.resolve('.' + key);
		}
		return ofNullable(target.getParent())
			.orElseGet(target::getRoot)
			.resolve('.' + target.getFileName().toString() + '.' + key);
	}

	public boolean isAttributeFile(Path path) {
		return path.getFileName().toString().startsWith(".");
	}

}
