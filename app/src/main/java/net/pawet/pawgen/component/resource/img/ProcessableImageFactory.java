package net.pawet.pawgen.component.resource.img;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.system.storage.Resource;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor(staticName = "of")
public final class ProcessableImageFactory {

	private final Consumer<BufferedImage> watermarkFilter;
	private final int thumbnailWidth;

	public Supplier<Map<String, String>> create(Resource img, Map<String, String> attributes) {
		return new ImageWithThumbnailProcessable(img, attributes, watermarkFilter, thumbnailWidth);
	}

}
