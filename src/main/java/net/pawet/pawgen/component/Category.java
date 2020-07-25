package net.pawet.pawgen.component;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;

@EqualsAndHashCode
@RequiredArgsConstructor(access = PRIVATE)
public final class Category implements Comparable<Category> {

	public static final Category ROOT = new Category(new String[0], "");
	private static final String CATEGORY_DELIMITER = "/";

	@NonNull
	private final String[] parts;
	private final String strVal;

	public static Category of(String... parts) {
		if (requireNonNull(parts, "Categories can't be empty").length == 0) {
			return ROOT;
		}
		assert Stream.of(parts).noneMatch(String::isEmpty) : "Category has illegal empty part";
		var sb = new StringBuilder(parts[0]);
		for (int i = 1; i < parts.length; i++) {
			sb.append('/').append(parts[i]);
		}
		return new Category(parts, sb.toString());
	}

	public Path resolveWith(Path path) {
		for (int i = 0; i < parts.length; i++) {
			path = path.resolve(parts[i]);
		}
		return path;
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

	@Override
	public String toString() {
		return strVal;
	}

	@Override
	public int compareTo(Category o) {
		return Arrays.compare(parts, o.parts);
	}

	public boolean isRoot() {
		return parts.length == 0;
	}

	public CharSequence relativize(String normalizedUrl) {
		if (normalizedUrl.isEmpty()) {
			return strVal;
		}
		if (normalizedUrl.charAt(0) != '/') { //relative
			return strVal + '/' + normalizedUrl;
		}
		int urlOffset = 1;
		int partNum = 0;
		while (partNum < parts.length) {
			String part = parts[partNum];
			if (!part.regionMatches(0, normalizedUrl, urlOffset, part.length())) {
				break;
			}
			urlOffset += part.length() + 1;
			partNum++;
		}

		var sb = new StringBuilder("../".repeat(parts.length - partNum));
		if (urlOffset < normalizedUrl.length()) {
			sb.append(normalizedUrl.substring(urlOffset));
		}
		return sb.toString();
	}

}
