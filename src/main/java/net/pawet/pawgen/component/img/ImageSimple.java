package net.pawet.pawgen.component.img;

import lombok.SneakyThrows;
import lombok.extern.java.Log;

import java.awt.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

import static net.pawet.pawgen.component.img.Image.calcDimension;

@Log
final class ImageSimple implements Image {

	private final Dimension dimension;
	private final Map<String, String> attrs;

	public ImageSimple(Dimension dimension, Map<String, String> attrs) {
		this.dimension = dimension;
		this.attrs = attrs;
	}

	@Override
	public Map<String, String> asAttrs() {
		var dimension = calcDimensions(this.dimension, getDimensionAttr("width"), getDimensionAttr("height"));
		return Map.of(
			"src", attrs.get("src"),
			"width", String.valueOf(dimension.width),
			"height", String.valueOf(dimension.height),
			"class", getClassOrDefault("img_left")
		);
	}

	private String getClassOrDefault(String cl) {
		String classAttr = attrs.get("class");
		return classAttr == null ? cl : classAttr;
	}

	private Integer getDimensionAttr(String name) {
		String value = attrs.get(name);
		return value == null ? null : Integer.parseInt(cleanImageSize(value));
	}

	private static String cleanImageSize(String s) {
		return s.endsWith("px") ? s.substring(0, s.length() - 2) : s;
	}

	@SneakyThrows
	@Override
	public final void processImage(Supplier<InputStream> srcProvider, Function<String, OutputStream> destProvider) {
		String src = attrs.get("src");
		try (var is = srcProvider.get(); var os = destProvider.apply(src)) {
			is.transferTo(os);
		} catch (FileAlreadyExistsException e) {
			log.log(Level.FINE, "File '{}' already exists", e.getFile());
		} catch (Exception e) {
			log.log(Level.SEVERE, "exception while saving thumbnail", e);
		}
	}

	private static Dimension calcDimensions(Dimension dimension, Integer widthAttr, Integer heightAttr) {
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

}
