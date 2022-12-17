package net.pawet.pawgen.component.system.storage;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Delegate;

import static lombok.AccessLevel.PACKAGE;
import static net.pawet.pawgen.component.system.storage.FileUtils.parseFileExt;

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
		return new AttachmentResource(resource, file, parseFileExt(file).toLowerCase());
	}

}
