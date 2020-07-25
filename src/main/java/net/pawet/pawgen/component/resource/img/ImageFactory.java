package net.pawet.pawgen.component.resource.img;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.resource.Processable;
import net.pawet.pawgen.component.system.storage.Resource;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor(staticName = "of")
public final class ImageFactory {

	private final Consumer<BufferedImage> watermarkFilter;

	public Processable<?> create(Map<String, String> attributes, Category category, Resource resource) {
		return new ImageWithThumbnailProcessable(ImageResource.of(resource), attributes, watermarkFilter, category);
	}

}
