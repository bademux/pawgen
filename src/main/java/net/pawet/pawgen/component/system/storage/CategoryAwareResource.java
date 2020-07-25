package net.pawet.pawgen.component.system.storage;

import lombok.*;
import net.pawet.pawgen.component.Category;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
public final class CategoryAwareResource implements ReadableResource {

	@ToString.Include
	@EqualsAndHashCode.Include
	private final Resource resource;
	@Getter
	private final Category category;

	static CategoryAwareResource of(Path relParentPath, Resource resource) {
		return new CategoryAwareResource(resource, createCategory(relParentPath));
	}

	private static Category createCategory(Path categoryPath) {
		if (categoryPath == null) {
			return Category.ROOT;
		}

		var categories = new String[categoryPath.getNameCount()];
		for (int i = 0; i < categories.length; i++) {
			categories[i] = categoryPath.getName(i).toString();
		}
		return Category.of(categories);
	}

	@SneakyThrows
	public Instant getLastModifiedTime() {
		return Files.getLastModifiedTime(resource.srcPath).toInstant();
	}

	@Override
	public InputStream inputStream() {
		return this.resource.inputStream();
	}

}
