package net.pawet.pawgen.component.resource.img;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.resource.Processable;
import net.pawet.pawgen.component.system.storage.ImageResource;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

	private final int thumbnailWidth = 250;

	@EqualsAndHashCode.Include
	private final ImageResource resource;
	@ToString.Include
	private final Map<String, String> attributes;
	private final Consumer<BufferedImage> watermarkFilter;
	private final Category category;

	@SneakyThrows
	@Override
	public Map<String, String> get() {
		try {
			AdvBufferedImage img = readImage();
			var dimension = new Dimension(img.image.getWidth(), img.image.getHeight());
			if (dimension.width > thumbnailWidth && hasThumbnailAttrs(attributes)) {
				return processImageWithThumbnail(img, dimension);
			}
			return processImage(dimension);
		} catch (Exception e) {
			log.warn("Can't process image, just coping", e);
		}
		Processable.transferSilently(resource);
		return attributes;
	}

	private Map<String, String> processImage(Dimension dimension) {
		Processable.transferSilently(resource);
		var calcDimensions = parseDimensions(dimension, getDimensionAttr("width", attributes), getDimensionAttr("height", attributes));
		return imageAttributes(calcDimensions, category.relativize(resource.getSrc()));
	}

	private static Integer getDimensionAttr(String name, Map<String, String> attrs) {
		String value = attrs.get(name);
		return value == null ? null : Integer.parseInt(cleanImageSize(value));
	}

	private static String cleanImageSize(String s) {
		return s.endsWith("px") ? s.substring(0, s.length() - 2) : s;
	}

	private static Dimension parseDimensions(Dimension dimension, Integer widthAttr, Integer heightAttr) {
		if (widthAttr != null && heightAttr != null) {
			return new Dimension(widthAttr, heightAttr);
		}
		if (widthAttr == null && heightAttr == null) {
			return dimension;
		}
		if (widthAttr != null) {
			return new Dimension(widthAttr, calcDimension(dimension, widthAttr));
		}
		return new Dimension(calcDimension(dimension, heightAttr), heightAttr);
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

	private Map<String, String> processImageWithThumbnail(AdvBufferedImage img, Dimension dimension) {
		var thumbnailDimension = getThumbnailDimension(dimension);
		String srcBase64 = "data:image/" + img.formatName + ";base64," + processThumbnail(img, thumbnailDimension);
		processWatermark(img);
		return thumbnailAttributes(thumbnailDimension, srcBase64);
	}

	private static boolean hasThumbnailAttrs(Map<String, String> attrs) {
		String classAttr = attrs.get("class");
		return classAttr == null || classAttr.startsWith("g_img_");
	}

	private Map<String, String> thumbnailAttributes(Dimension dimension, String src) {
		String alt = attributes.get("alt");
		String title = alt == null ? attributes.get("src") : alt + '.';
		var attr = new HashMap<String, String>();
		attr.put("src", src);
		attr.put("title", title);
		attr.put("alt", title);
		attr.put("width", String.valueOf(dimension.width));
		attr.put("height", String.valueOf(dimension.height));
		attr.put("class", attributes.getOrDefault("class", "g_img"));
		attr.put("onClick", "showLightbox(this, '" + attributes.get("src") + "')");
		attributes.forEach(attr::putIfAbsent);
		return attr;
	}

	private void processWatermark(AdvBufferedImage img) {
		try (var os = resource.outputStream()) {
			watermarkFilter.accept(img.image);
			writeImage(img.image, img.formatName, os);
		} catch (FileAlreadyExistsException e) {
			log.debug("File '{}' already exists", e.getFile());
		} catch (IOException e) {
			log.error("exception while processing image '{}'", this, e);
		}
	}

	private final static Base64.Encoder BASE64_ENCODER = Base64.getEncoder();

	private String processThumbnail(AdvBufferedImage img, Dimension thumbnailDimension) {
		var byteOs = new ByteArrayOutputStream(thumbnailWidth * thumbnailWidth * 3);
		try (var os = BASE64_ENCODER.wrap(byteOs)) {
			BufferedImage thumbnailImage = resize(img.image, thumbnailDimension);
			writeImage(thumbnailImage, img.formatName, os);
		} catch (FileAlreadyExistsException e) {
			log.debug("File '{}' already exists", e.getFile());
		} catch (IOException e) {
			log.error("exception while processing image '{}'", resource.getThumbnailSrc(), e);
		}
		return byteOs.toString();
	}

	private Dimension getThumbnailDimension(Dimension dimension) {
		int height = calcDimension(dimension, thumbnailWidth);
		return new Dimension(thumbnailWidth, height);
	}

	static int calcDimension(Dimension dimension, int destDimension) {
		return (int) round(dimension.getHeight() / dimension.getWidth() * destDimension);
	}

	private static void writeImage(BufferedImage image, String formatName, OutputStream os) throws IOException {
		try (var stream = requireNonNull(ImageIO.createImageOutputStream(os))) {
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

	private AdvBufferedImage readImage() throws IOException {
		try (var is = resource.inputStream()) {
			return read(is);
		}
	}

	private AdvBufferedImage read(InputStream is) throws IOException {
		try (var iis = ImageIO.createImageInputStream(is)) {
			@Cleanup("dispose") var reader = getImageReaderBy(iis);
			reader.setInput(iis, true, true);
			log.debug("Reading image {} with format {}", resource.getSrc(), reader.getFormatName());
			BufferedImage image = reader.read(0, reader.getDefaultReadParam());
			return new AdvBufferedImage(image, reader.getFormatName());
		}
	}

	private ImageReader getImageReaderBy(javax.imageio.stream.ImageInputStream stream) {
		return Optional.ofNullable(ImageIO.getImageReaders(stream))
			.filter(Iterator::hasNext)
			.map(Iterator::next)
			.orElseThrow(() -> new IllegalArgumentException("No reader for image"));
	}

	static BufferedImage resize(BufferedImage img, Dimension dimension) {
		var thumbnailImage = new BufferedImage(dimension.width, dimension.height, img.getType());
		var graphics = thumbnailImage.createGraphics();
		try {
			graphics.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
			graphics.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
			graphics.drawImage(img, 0, 0, dimension.width, dimension.height, null);
			return thumbnailImage;
		} finally {
			graphics.dispose();
		}
	}

		private record AdvBufferedImage(BufferedImage image, String formatName) {
	}
}
