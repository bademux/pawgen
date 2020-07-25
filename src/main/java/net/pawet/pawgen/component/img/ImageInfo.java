package net.pawet.pawgen.component.img;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.function.Supplier;

import static lombok.AccessLevel.PRIVATE;

@Getter
@RequiredArgsConstructor(access = PRIVATE)
final class ImageInfo {
	private final Dimension dimension;
	private final String formatName;

	public static ImageInfo parse(InputStream is) throws IOException {
		try (var imageIS = ImageIO.createImageInputStream(is)) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(imageIS);
			if (!readers.hasNext()) {
				throw new IIOException("No reader for image");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(imageIS);
				int imageIndex = reader.getMinIndex();
				Dimension dimension = new Dimension(reader.getWidth(imageIndex), reader.getHeight(imageIndex));
				return new ImageInfo(dimension, reader.getFormatName());
			} finally {
				reader.dispose();
			}
		}
	}

}
