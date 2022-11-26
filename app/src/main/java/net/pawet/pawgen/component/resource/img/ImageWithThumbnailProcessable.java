package net.pawet.pawgen.component.resource.img;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.system.storage.ImageResource;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.awt.RenderingHints.*;
import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;
import static javax.imageio.metadata.IIOMetadataFormatImpl.standardMetadataFormatName;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class ImageWithThumbnailProcessable implements Supplier<Map<String, String>> {


	@EqualsAndHashCode.Include
	private final ImageResource resource;
	@ToString.Include
	private final Map<String, String> attributes;
	private final Consumer<BufferedImage> watermarkFilter;
	@ToString.Include
	private final int thumbnailWidth;

	@SneakyThrows
	@Override
	public Map<String, String> get() {
		try {
			var img = readImage();
			BufferedImage image = img.getValue();
			String formatName = img.getKey();
			if (isGreaterThanThumbnail(image.getWidth()) && hasThumbnailAttrs()) {
				var attrs = processThumbnail(image, formatName);
				writeWatermarkedImage(image, formatName);
				resource.transfer();
				return attrs;
			}
			return processImage(image, formatName);
		} catch (Exception e) {
			log.warn("Can't process image, just coping", e);
		}
		return attributes;
	}

	private Map<String, String> processImage(BufferedImage image, String formatName) {
		var calcDimensions = parseDimensions(image.getWidth(), image.getHeight(), getDimensionAttr("width", attributes), getDimensionAttr("height", attributes));
		String srcBase64 = getAsBase64(image, formatName);
		return imageAttributes(calcDimensions, srcBase64);
	}

	private static Integer getDimensionAttr(String name, Map<String, String> attrs) {
		String value = attrs.get(name);
		return value == null ? null : Integer.parseInt(cleanImageSize(value));
	}

	private static String cleanImageSize(String s) {
		return s.endsWith("px") ? s.substring(0, s.length() - 2) : s;
	}

	private static Dimension parseDimensions(int width, int height, Integer widthAttr, Integer heightAttr) {
		if (widthAttr != null && heightAttr != null) {
			return new Dimension(widthAttr, heightAttr);
		}
		if (widthAttr == null && heightAttr == null) {
			return new Dimension(width,height);
		}
		if (widthAttr != null) {
			return new Dimension(widthAttr, calcDimension(width, height, widthAttr));
		}
		return new Dimension(calcDimension(width, height, heightAttr), heightAttr);
	}

	private Map<String, String> imageAttributes(Dimension dimension, String src) {
		var attr = new HashMap<String, String>();
		attr.put("src", src);
		attr.put("width", String.valueOf(dimension.width));
		attr.put("height", String.valueOf(dimension.height));
		attr.put("class", attributes.getOrDefault("class", "img_left"));
		attributes.forEach(attr::putIfAbsent);
		return attr;
	}

	private Map<String, String> processThumbnail(BufferedImage image, String formatName) {
		var thumbnailHeight = getThumbnailHeight(image.getWidth(), image.getHeight());
		String targetFormat = image.getColorModel().hasAlpha() ? formatName : "jpg";
		String srcBase64 = getAsBase64(resize(image, thumbnailWidth, thumbnailHeight), targetFormat);
		return thumbnailAttributes(srcBase64, thumbnailWidth, thumbnailHeight);
	}

	private boolean hasThumbnailAttrs() {
		String classAttr = attributes.get("class");
		return classAttr == null || classAttr.startsWith("g_img_");
	}

	private boolean isGreaterThanThumbnail(int width) {
		return width > thumbnailWidth;
	}

	private Map<String, String> thumbnailAttributes(String src, int width, int height) {
		String alt = attributes.get("alt");
		String title = alt == null ? attributes.get("src") : alt + '.';
		var attr = new HashMap<String, String>();
		attr.put("src", src);
		attr.put("title", title);
		attr.put("alt", title);
		attr.put("width", String.valueOf(width));
		attr.put("height", String.valueOf(height));
		attr.put("class", attributes.getOrDefault("class", "g_img"));
		attr.put("onClick", "showLightbox(this, '" + attributes.get("src") + "')");
		attributes.forEach(attr::putIfAbsent);
		return attr;
	}

	private void writeWatermarkedImage(BufferedImage image, String formatName) {
		try (var out = resource.writable()) {
			watermarkFilter.accept(image);
			writeImage(image, formatName, out);
		} catch (Exception e) {
			if(e instanceof FileAlreadyExistsException ef) {
				log.trace("File already exists '{}' skipping '{}'", this, ef.getFile());
				return;
			}
			log.error("Problem while watermarking image '{}'", this, e);
		}
	}

	private final static Base64.Encoder BASE64_ENCODER = Base64.getEncoder();

	@SneakyThrows
	private String getAsBase64(BufferedImage thumbnailImage, String formatName) {
		formatName = "gif".equals(formatName) ? "png" : formatName; //force embed PNG for GIFs
		var bos = new ByteArrayOutputStream(thumbnailImage.getWidth() * thumbnailImage.getHeight() * 3);
		try (var os = BASE64_ENCODER.wrap(bos)) {
			writeImage(thumbnailImage, formatName, os);
			return "data:image/%s;base64,%s".formatted(formatName, bos.toString());
		}
	}

	private int getThumbnailHeight(int width, int height) {
		return calcDimension(width, height, thumbnailWidth);
	}

	static int calcDimension(int width, int height, int destDimension) {
		return (int) round(((double) height / width) * destDimension);
	}

	private static void writeImage(BufferedImage image, String formatName, Object out) throws IOException {
		try (var stream = requireNonNull(ImageIO.createImageOutputStream(out))) {
			@Cleanup("dispose") var imageWriter = getImageWriterBy(formatName);
			imageWriter.setOutput(stream);
			ImageWriteParam param = imageWriter.getDefaultWriteParam();
			setCompressionQualityIfPossible(formatName, param);
			var metadata = createMetadata(imageWriter, image.getType(), param);
			imageWriter.write(null, new IIOImage(image, null, metadata), param);
			stream.flush();
		}
	}

	private static ImageWriter getImageWriterBy(String formatName) {
		return Optional.ofNullable(ImageIO.getImageWritersByFormatName(formatName))
			.filter(Iterator::hasNext)
			.map(Iterator::next)
			.orElseThrow(() -> new IllegalArgumentException("no writer found for image"));
	}

	private static void setCompressionQualityIfPossible(String formatName, ImageWriteParam param) {
		if (param.canWriteCompressed()) {
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			initWithFirstAvailableComressionIfNeeded(param);
			param.setCompressionQuality(getCompressionQuality(formatName));
		}
	}

	private static IIOMetadata createMetadata(ImageWriter imageWriter, int imageType, ImageWriteParam param) {
		try {
			ImageTypeSpecifier imageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(imageType);
			IIOMetadata metadata = imageWriter.getDefaultImageMetadata(imageTypeSpecifier, param);
			metadata.mergeTree(standardMetadataFormatName, createTextEntry("Source", "pawgen"));
			return metadata;
		} catch (Exception e) {
			return null;
		}
	}

	private static IIOMetadataNode createTextEntry(final String key, final String value) {
		IIOMetadataNode textEntry = new IIOMetadataNode("TextEntry");
		textEntry.setAttribute("keyword", key);
		textEntry.setAttribute("value", value);

		IIOMetadataNode text = new IIOMetadataNode("Text");
		text.appendChild(textEntry);

		IIOMetadataNode root = new IIOMetadataNode(standardMetadataFormatName);
		root.appendChild(text);
		return root;
	}

	private static void initWithFirstAvailableComressionIfNeeded(ImageWriteParam param) {
		if (param.getCompressionType() == null) {
			String[] compressionTypes = param.getCompressionTypes();
			if (compressionTypes != null && compressionTypes.length > 0) {
				param.setCompressionType(compressionTypes[0]);
			}
		}
	}

	private static float getCompressionQuality(String formatName) {
		return "png".equals(formatName) || "gif".equals(formatName) ? 0f : 0.7f;
	}

	private Entry<String, BufferedImage> readImage() throws IOException {
		try (var channel = resource.readable()) {
			return read(channel);
		}
	}

	private Entry<String, BufferedImage> read(Object channel) throws IOException {
		try (var iis =  requireNonNull(ImageIO.createImageInputStream(channel))) {
			@Cleanup("dispose") var reader = getImageReaderBy(iis);
			reader.setInput(iis, true, true);
			log.debug("Reading image {} with format {}", resource.getSrc(), reader.getFormatName());
			BufferedImage image = reader.read(0, reader.getDefaultReadParam());
			return Map.entry(reader.getFormatName(), image);
		}
	}

	static ImageReader getImageReaderBy(javax.imageio.stream.ImageInputStream stream) {
		return Optional.ofNullable(ImageIO.getImageReaders(stream))
			.filter(Iterator::hasNext)
			.map(Iterator::next)
			.orElseThrow(() -> new IllegalArgumentException("No reader for image"));
	}

	static BufferedImage resize(BufferedImage img, int width, int height) {
		var thumbnailImage = new BufferedImage(width, height, img.getType());
		@Cleanup("dispose") var graphics = thumbnailImage.createGraphics();
		graphics.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
		graphics.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);
		graphics.drawImage(img, 0, 0, width, height, null);
		return thumbnailImage;
	}

}
