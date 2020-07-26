package net.pawet.pawgen.component.img;

import com.criteo.vips.VipsImage;
import com.criteo.vips.enums.VipsImageFormat;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import java.awt.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;
import static lombok.AccessLevel.PRIVATE;

@Log
@RequiredArgsConstructor(access = PRIVATE)
final class ImageThumbnail implements Image {

	private final Map<String, String> attrs;
	private final Dimension thumbnailDimension;
	private final WatermarkFilter watermarkFilter;


	public static Image of(Map<String, String> attrs, WatermarkFilter watermarkFilter, ImageInfo imageInfo, int thumbnailWidth) {
		Dimension targetDimension = new Dimension(thumbnailWidth, Image.calcDimension(imageInfo.getDimension(), thumbnailWidth));
		return new ImageThumbnail(attrs, targetDimension, watermarkFilter);
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
	public final void processImage(Supplier<FileChannel> srcProvider, Function<String, FileChannel> destProvider) {
		try (FileChannel ch = srcProvider.get();
			 VipsImage image = new VipsImage(ch.map(READ_ONLY, 0, ch.size()), (int) ch.size())) {
			processThumbnail(image, destProvider);
			processWatermark(image, destProvider);
		}
	}

	private void processWatermark(VipsImage img, Function<String, FileChannel> destProvider) {
		String src = attrs.get("src");
		try (var os = destProvider.apply(src)) {
			watermarkFilter.accept(img);
			os.write(ByteBuffer.wrap(img.writeToArray(VipsImageFormat.JPG, true)));
		} catch (FileAlreadyExistsException e) {
			log.log(Level.FINE, "File '{}' already exists", e.getFile());
		} catch (IOException e) {
			log.log(Level.SEVERE, e, () -> "exception while processing image " + src);
		}
	}

	private void processThumbnail(VipsImage img, Function<String, FileChannel> destProvider) {
		String thumbnailSrc = getThumbnailSrc();
		try (var os = destProvider.apply(thumbnailSrc)) {
			img.resize(thumbnailDimension, true);
			os.write(ByteBuffer.wrap(img.writeToArray(VipsImageFormat.JPG, true)));
		} catch (FileAlreadyExistsException e) {
			log.log(Level.FINE, "File '{}' already exists", e.getFile());
		} catch (IOException e) {
			log.log(Level.SEVERE, e, () -> "exception while processing image " + thumbnailSrc);
		}
	}

}
