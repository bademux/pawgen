package util;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import groovy.lang.Writable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.codehaus.groovy.runtime.EncodingGroovyMethods;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import static java.nio.file.FileSystems.newFileSystem;
import static java.nio.file.Files.*;
import static java.util.Collections.reverseOrder;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static lombok.AccessLevel.PROTECTED;

@RequiredArgsConstructor(access = PROTECTED)
public class PawgenFs implements AutoCloseable {

	@Getter
	private final FileSystem fs;
	private final Path rootDir;

	@SneakyThrows
	public static PawgenFs tmpZipFs() {
		Path tmp = createTempDirectory("pawgen");
		FileSystem fs = newFileSystem(new URI("jar", tmp.resolve("paw.zip").toUri().toString(), null),
			Map.of("create", true, "useTempFile", false, "noCompression", true));
		return new PawgenFs(fs, fs.getRootDirectories().iterator().next());
	}

	@SneakyThrows
	public static PawgenFs tmpFs() {
		Path tmp = createTempDirectory("pawgen");
		return new PawgenFs(tmp.getFileSystem(), tmp);
	}

	@SneakyThrows
	public static PawgenFs unix() {
		return create(Configuration.unix());
	}

	@SneakyThrows
	public static PawgenFs unixWithUserAttrs() {
		return create(Configuration.unix().toBuilder().setAttributeViews("basic", "user").build());
	}

	@SneakyThrows
	public static PawgenFs win() {
		return create(Configuration.windows());
	}

	@SneakyThrows
	public static PawgenFs osx() {
		return create(Configuration.osX());
	}

	@SneakyThrows
	private static PawgenFs create(Configuration configuration) {
		FileSystem fs = Jimfs.newFileSystem(configuration);
		Path rootDir = fs.getRootDirectories().iterator().next().resolve("pawgen_root");
		return new PawgenFs(fs, rootDir);
	}

	@Override
	public void close() throws Exception {
		var toBeDeleted = rootDir;
		var store = rootDir.getFileSystem().getFileStores().iterator().next();
		switch (store.type()) {
			case "jimfs":
				fs.close();
				return;
			case "zipfs":
				fs.close();
				toBeDeleted = Paths.get(store.name()).getParent();
			default:
				walk(toBeDeleted).sorted(reverseOrder()).forEach(PawgenFs::delete);
		}

	}

	@SneakyThrows
	private static void delete(Path path) {
		Files.delete(path);
	}

	public Map<Path, String> readAttributes(Path outputDir, String attrName) {
		return listDir(outputDir).map(path -> new SimpleImmutableEntry<>(path, readAttribute(path, attrName)))
			.filter(entry -> entry.getValue() != null)
			.collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	@SneakyThrows
	static Stream<Path> listDir(Path outputDir) {
		return walk(outputDir).filter(PawgenFs::isNotHiddenOrAttribute);
	}

	@SneakyThrows
	private static boolean isNotHiddenOrAttribute(Path path) {
		return !Files.isHidden(path) && !(path.getFileName() != null && path.getFileName().toString().startsWith("."));
	}

	@SneakyThrows
	protected String readAttribute(Path path, String attrName) {
		if (path.getFileSystem().supportedFileAttributeViews().contains("user")) {
			UserDefinedFileAttributeView attributeView = getFileAttributeView(path, UserDefinedFileAttributeView.class);
			assert attributeView != null;
			if (!attributeView.list().contains(attrName)) {
				return null;
			}
			ByteBuffer digestBuff = ByteBuffer.allocate(attributeView.size(attrName));
			attributeView.read(attrName, digestBuff);
			return encodeHex(digestBuff.array());
		}
		Path attrFile = getAttrFile(path, attrName);
		if (Files.exists(attrFile)) {
			return encodeHex(Files.readAllBytes(attrFile));
		}
		return null;
	}

	private String encodeHex(byte[] data) throws IOException {
		Writable writable = EncodingGroovyMethods.encodeHex(data);
		Writer writer = new StringWriter();
		writable.writeTo(writer);
		return writer.toString();
	}

	@NotNull
	private Path getAttrFile(Path path, String attrName) {
		if (Files.isDirectory(path)) {
			return path.resolve('.' + attrName);
		}
		return ofNullable(path.getParent()).orElseGet(path::getRoot).resolve('.' + path.getFileName().toString() + '.' + attrName);

	}

	@SneakyThrows
	public Set<Path> listFiles(Path outputDir) {
		return listDir(outputDir).filter(Files::isRegularFile).collect(toSet());
	}

	@SneakyThrows
	public Path dir(String path) {
		return createDirectories(rootDir.resolve(path));
	}

	@SneakyThrows
	public Path file(String file, byte[] content) {
		Path path = rootDir.resolve(file);
		createDirectories(path.getParent());
		write(path, content);
		return path;
	}

	@SneakyThrows
	public Path writeProperties(String path, Map<String, String> config) {
		Path file = rootDir.resolve(path);
		try (var writer = newBufferedWriter(file)) {
			var properties = new Properties();
			properties.putAll(config);
			properties.store(writer, null);
			return file;
		}
	}

}
