package net.pawet.pawgen.component.system.storage;

import lombok.*;
import lombok.experimental.Delegate;

@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class ImageResource implements Resource {

	@EqualsAndHashCode.Include
	@Delegate(types = {ReadableResource.class, WritableResource.class})
	private final Resource resource;
	@EqualsAndHashCode.Include
	private final ReadableResource resourceThumb;
	@Getter
	private final String thumbnailSrc;
	@Getter
	private final String src;

	public static ImageResource of(Resource resource, String src) {
		String thumbnailSrc = "/thumbnails/" + src;
		var res = (SimpleResource) resource;
		var thumbnail = res.storage.resource(res.srcPath, thumbnailSrc).orElseThrow();;
		return new ImageResource(resource, thumbnail, thumbnailSrc, src);
	}

}
