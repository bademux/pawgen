package net.pawet.pawgen.component.resource.img;

import lombok.*;
import lombok.experimental.Delegate;
import net.pawet.pawgen.component.system.storage.ReadableResource;
import net.pawet.pawgen.component.system.storage.Resource;
import net.pawet.pawgen.component.system.storage.WritableResource;

import java.io.OutputStream;

@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class ImageResource implements ReadableResource, WritableResource {

	@EqualsAndHashCode.Include
	@Delegate(types = {ReadableResource.class, WritableResource.class})
	private final Resource resource;
	@EqualsAndHashCode.Include
	private final Resource resourceThumb;
	@Getter
	private final String thumbnailSrc;

	public static ImageResource of(Resource resource) {
		String thumbnailSrc = "/thumbnails/" + resource.getRootRelativePath();
		return new ImageResource(resource, resource.withDestPath(thumbnailSrc), thumbnailSrc);
	}

	public OutputStream outputStreamThumbnail() {
		return resourceThumb.outputStream();
	}

	public String getSrc() {
		return resource.getRootRelativePath();
	}

}
