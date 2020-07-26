package net.pawet.pawgen.component.img;

import java.awt.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.Math.round;

interface Image {

	Map<String, String> asAttrs();

	void processImage(Supplier<FileChannel> srcProvider, Function<String, FileChannel> destProvider);

	static int calcDimension(Dimension dimension, int destDimension) {
		return (int) round(dimension.getHeight() / dimension.getWidth() * destDimension);
	}
}
