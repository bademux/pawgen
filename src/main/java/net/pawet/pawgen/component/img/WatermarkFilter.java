package net.pawet.pawgen.component.img;

import com.criteo.vips.Image;
import com.criteo.vips.VipsImage;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import static lombok.AccessLevel.PRIVATE;

@RequiredArgsConstructor(access = PRIVATE)
public final class WatermarkFilter implements AutoCloseable {

	private final Image watermarkImg;

	public static WatermarkFilter of(Path watermarkFile) {
		return new WatermarkFilter(createWatermarkImage(watermarkFile));
	}

	public void accept(VipsImage img) {
		img.compose(watermarkImg);
	}

	@SneakyThrows
	static VipsImage createWatermarkImage(Path watermarkFile) {
		byte[] data = Files.readAllBytes(watermarkFile);
		return new VipsImage(data, data.length);
	}

	@Override
	public void close() throws Exception {
		watermarkImg.close();
	}
}
