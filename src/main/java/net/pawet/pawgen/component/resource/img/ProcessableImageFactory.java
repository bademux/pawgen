package net.pawet.pawgen.component.resource.img;

import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.system.storage.ImageResource;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public record ProcessableImageFactory(Consumer<BufferedImage> watermarkFilter) {

	public Supplier<Map<String, String>> create(Map<String, String> attributes, Category category, ImageResource img) {
		return new ImageWithThumbnailProcessable(img, attributes, watermarkFilter, category);
	}

}
