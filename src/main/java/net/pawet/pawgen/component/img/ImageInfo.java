package net.pawet.pawgen.component.img;

import com.criteo.vips.VipsImage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.awt.*;
import java.io.IOException;
import java.nio.channels.FileChannel;

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;
import static lombok.AccessLevel.PRIVATE;

@Getter
@RequiredArgsConstructor(access = PRIVATE)
final class ImageInfo {
	private final Dimension dimension;

	public static ImageInfo parse(FileChannel channel) throws IOException {
		var buf = channel.map(READ_ONLY, 0, channel.size());
		try (var image = new VipsImage(buf, (int) channel.size())) {
			Dimension dimension = new Dimension(image.getWidth(), image.getHeight());
			return new ImageInfo(dimension);
		}
	}

}
