package net.pawet.pawgen.component.img;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import javax.imageio.*;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

import static java.awt.RenderingHints.*;
import static java.util.Objects.requireNonNull;
import static javax.imageio.metadata.IIOMetadataFormatImpl.standardMetadataFormatName;
import static lombok.AccessLevel.PRIVATE;

@Log
@RequiredArgsConstructor(access = PRIVATE)
final class ImageThumbnail implements Image {

	private final Map<String, String> attrs;
	private final String formatName;
	private final Dimension thumbnailDimension;
	private final WatermarkFilter watermarkFilter;


	public static Image of(Map<String, String> attrs, WatermarkFilter watermarkFilter, ImageInfo imageInfo, int thumbnailWidth) {
		Dimension targetDimension = new Dimension(thumbnailWidth, Image.calcDimension(imageInfo.getDimension(), thumbnailWidth));
		return new ImageThumbnail(attrs, imageInfo.getFormatName(), targetDimension, watermarkFilter);
	}

	@Override
	public Map<String, String> asAttrs() {
		String alt = attrs.get("alt");
		String title = alt == null ? attrs.get("src") : alt + '.';
		return Map.of(
			"src", getThumbnailSrc(),
			"title", title,
			"alt", title,
			"width", String.valueOf(thumbnailDimension.width),
			"height", String.valueOf(thumbnailDimension.height),
			"class", getClassOrDefault("g_img"),
			"onClick", "showLightbox(this, '" + attrs.get("src") + "')"
		);
	}

	private String getClassOrDefault(String cl) {
		String classAttr = attrs.get("class");
		return classAttr == null ? cl : classAttr;
	}

	private String getThumbnailSrc() {
		String src = attrs.get("src");
		int dotPos = src.lastIndexOf('.');
		if (dotPos == -1) {
			return src + ".thumb";
		}
		return src.substring(0, dotPos) + ".thumb" + src.substring(dotPos);
	}

	@SneakyThrows
	@Override
	public final void processImage(Supplier<InputStream> srcProvider, Function<String, OutputStream> destProvider) {
		BufferedImage img = readImage(srcProvider);
		processThumbnail(img, destProvider);
		processWatermark(destProvider, img);
	}

	private void processWatermark(Function<String, OutputStream> destProvider, BufferedImage img) {
		String src = attrs.get("src");
		try (var os = destProvider.apply(src)) {
			watermarkFilter.accept(img);
			writeImage(img, formatName, os);
		} catch (FileAlreadyExistsException e) {
			log.log(Level.FINE, "File '{}' already exists", e.getFile());
		} catch (IOException e) {
			log.log(Level.SEVERE, e, () -> "exception while processing image " + src);
		}
	}

	private void processThumbnail(BufferedImage img, Function<String, OutputStream> destProvider) {
		String thumbnailSrc = getThumbnailSrc();
		try (var os = destProvider.apply(thumbnailSrc)) {
			BufferedImage thumbnailImage = resize(img, thumbnailDimension);
			writeImage(thumbnailImage, "jpg", os);
		} catch (FileAlreadyExistsException e) {
			log.log(Level.FINE, "File '{}' already exists", e.getFile());
		} catch (IOException e) {
			log.log(Level.SEVERE, e, () -> "exception while processing image " + thumbnailSrc);
		}
	}

	private static void writeImage(BufferedImage image, String formatName, OutputStream os) throws IOException {
		Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName(formatName);
		if (!it.hasNext()) {
			throw new IllegalArgumentException("no writer found for image");
		}
		ImageWriter imageWriter = it.next();
		try (var stream = requireNonNull(ImageIO.createImageOutputStream(os))) {
			imageWriter.setOutput(stream);
			ImageWriteParam param = imageWriter.getDefaultWriteParam();
			setCompressionQualityIfPossible(formatName, param);
			var metadata = createMetadata(imageWriter, image.getType(), param);
			imageWriter.write(null, new IIOImage(image, null, metadata), param);
			stream.flush();
		} finally {
			imageWriter.dispose();
		}
	}

	private static void setCompressionQualityIfPossible(String formatName, ImageWriteParam param) {
		if (param.canWriteCompressed()) {
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			initWithFirstAvailableComressionIfNeeded(param);
			param.setCompressionQuality(getCompresionQuality(formatName));
		}
	}

	private static IIOMetadata createMetadata(ImageWriter imageWriter, int imageType, ImageWriteParam param) throws IIOInvalidTreeException {
		ImageTypeSpecifier imageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(imageType);
		IIOMetadata metadata = imageWriter.getDefaultImageMetadata(imageTypeSpecifier, param);
		metadata.mergeTree(standardMetadataFormatName, createTextEntry("Source", "pawgen"));
		return metadata;
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

	private static float getCompresionQuality(String formatName) {
		return "png".equals(formatName) || "gif".equals(formatName) ? 0f : 0.7f;
	}

	private BufferedImage readImage(Supplier<InputStream> srcProvider) throws IOException {
		try (var is = srcProvider.get()) {
			return requireNonNull(ImageIO.read(is));
		}
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

}
