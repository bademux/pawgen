package net.pawet.pawgen.component.system.storage;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import net.pawet.pawgen.component.Category;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;

import static lombok.AccessLevel.PROTECTED;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = PROTECTED)
public final class CategoryAwareResource implements ReadableResource {

	@Getter
	private final Category category;
	@ToString.Include
	@EqualsAndHashCode.Include
	final Path srcPath;
	final Storage storage;

	@Override
	public InputStream inputStream() {
		return storage.read(srcPath);
	}

	public Instant getModificationDate() {
		return storage.getModificationDate(srcPath);
	}

}
