package net.pawet.pawgen.component.system.storage;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Delegate;

import static lombok.AccessLevel.PACKAGE;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = PACKAGE)
public final class AttachmentResource implements Resource {

	@Delegate(types = Resource.class)
	@ToString.Include
	@EqualsAndHashCode.Include
	private final Resource resource;
	@Getter
	private final String uri;
	@Getter
	private final String type;

	public static AttachmentResource of(Resource resource, String file) {
		return new AttachmentResource(resource, file, parseFileExt(file));
	}

	static String parseFileExt(String file) {
		if (file == null) {
			return null;
		}
		int dotIndex = file.lastIndexOf('.');
		if (dotIndex == -1 || dotIndex == file.length() - 1) {
			return null;
		}
		return file.substring(dotIndex + 1).toLowerCase();
	}

}
