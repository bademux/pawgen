package net.pawet.pawgen.component.img;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.Storage;

import java.awt.*;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

// original <img src="_img/prawdzic.png" align="right" class="img_left"></img>
// processed <img src="/cache/genealogy/chrul/_img/thumb_jotki.jpg" height="174" width="250" alt="(29KB) jotki.jpg" title="jotki.jpg" class="g_img" onClick="showLightbox(this, '/cache/genealogy/chrul/_img/jotki.jpg')"/>
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class ImageParser {

	private final ExecutorService executor;
	private final Storage storage;
	private final int thumbnailWidth;
	private final WatermarkFilter watermarkFilter;

	public static ImageParser of(ExecutorService imageProcessingExecutor, Storage storage, String watermarkText) {
		return new ImageParser(imageProcessingExecutor, storage, 250, WatermarkFilter.of(watermarkText, 0.7f));
	}

	@SneakyThrows
	public Map<String, String> createImgAttributes(Map<String, String> attributes, Category category) {
		String src = requireNonNull(attributes.get("src"));
		try {
			ImageInfo imageInfo = parseImageInfo(category, src);
			Image image = createImage(imageInfo, attributes);
			executor.execute(() -> processImage(image, category, src));
			return image.asAttrs();
		} catch (Exception e) {
			return Map.of("src", "/res/img/noimage.png", "alt", format("no image %s/%s founded", category, src));
		}
	}

	private ImageInfo parseImageInfo(Category category, String src) throws IOException {
		try (var is = storage.inputStream(category, src)) {
			return ImageInfo.parse(is);
		}
	}

	private void processImage(Image image, Category category, String src) {
		try {
			image.processImage(
				() -> storage.inputStream(category, src),
				s -> storage.outputStream(category, s)
			);
		} catch (Exception e) {
			log.warn("Error while processing image {}/{}", category, src, e);
		}
	}

	private boolean hasThumbnail(Dimension dimension, Map<String, String> attrs) {
		if (dimension.width <= thumbnailWidth) {
			return false;
		}
		String classAttr = attrs.get("class");
		return classAttr == null || classAttr.startsWith("g_img_");
	}

	private Image createImage(ImageInfo imageInfo, Map<String, String> attrs) {
		if (hasThumbnail(imageInfo.getDimension(), attrs)) {
			return ImageThumbnail.of(attrs, watermarkFilter, imageInfo, thumbnailWidth);
		}
		return new ImageSimple(imageInfo.getDimension(), attrs);
	}

}
