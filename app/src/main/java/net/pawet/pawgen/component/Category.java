package net.pawet.pawgen.component;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static java.util.Arrays.copyOfRange;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;

@EqualsAndHashCode
@RequiredArgsConstructor(access = PRIVATE)
public final class Category implements Comparable<Category> {

	public static final Category ROOT = new Category(new String[0]);

	@NonNull
	private final String[] parts;
	@Getter(lazy = true, value = PRIVATE)
	private final String asString = createStringValue();

	public static Category of(Path categoryPath) {
		if (categoryPath == null || categoryPath.getFileName().toString().isEmpty()) {
			return Category.ROOT;
		}

		var categories = new String[categoryPath.getNameCount()];
		for (int i = 0; i < categories.length; i++) {
			categories[i] = categoryPath.getName(i).toString();
		}
		return Category.of(categories);
	}

	public static Category of(String... parts) {
		if (requireNonNull(parts, "Categories can't be empty").length == 0) {
			return ROOT;
		}
		assert Stream.of(parts).noneMatch(String::isEmpty) : "Category has illegal empty part";
		return new Category(parts);
	}

	public Path resolveWith(Path path) {
		for (String part : parts) {
			path = path.resolve(part);
		}
		return path;
	}

	public Category getParent() {
		if (parts.length == 0) {
			return null;
		}
		int parentCategoryLastPartIndex = parts.length - 1;
		if (parentCategoryLastPartIndex == 0) {
			return ROOT;
		}
		return new Category(copyOfRange(parts, 0, parentCategoryLastPartIndex));
	}


	public boolean isChildFor(Category parent) {
		// parent is root
		if (parts.length == 1 && parent.parts.length == 0) {
			return true;
		}
		if (parts.length == parent.parts.length + 1) {
			return Arrays.compare(
				parts, 0, parent.parts.length,
				parent.parts, 0, parent.parts.length) == 0;
		}
		return false;
	}

	private String createStringValue() {
		return String.join("/", parts);
	}

	@Override
	public String toString() {
		return getAsString();
	}

	@Override
	public int compareTo(Category o) {
		return Arrays.compare(parts, o.parts);
	}

	public boolean isRoot() {
		return parts.length == 0;
	}

	public String relativize(String path) {
		if (!path.startsWith("/")) { //relative
			String categoryStr = toString();
			if (!path.startsWith(categoryStr)) {
				return path;
			}
			int from = categoryStr.length();
			if (path.length() > from && path.charAt(from) == '/') {
				from++;
			}
			return path.substring(from);
		}
		int urlOffset = 1;
		int partNum = 0;
		while (partNum < parts.length) {
			String part = parts[partNum];
			if (!part.regionMatches(0, path, urlOffset, part.length())) {
				break;
			}
			urlOffset += part.length() + 1;
			partNum++;
		}

		var sb = new StringBuilder("../".repeat(parts.length - partNum));
		if (urlOffset < path.length()) {
			sb.append(path.substring(urlOffset));
		}
		return sb.toString();
	}

	public String resolve(String url) {
		if (parts.length == 0) {
			return url;
		}
		if (url.startsWith("/")) {
			return url;
		}
		return toString() + '/' + url;
	}

	public boolean endsWith(String pathPart) {
		return toString().endsWith(pathPart);
	}


}
