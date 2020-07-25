package net.pawet.pawgen.component.img;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.awt.Color.BLACK;
import static java.awt.Color.LIGHT_GRAY;
import static java.awt.Font.ITALIC;
import static java.awt.Font.SANS_SERIF;
import static java.awt.RenderingHints.KEY_TEXT_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;

@RequiredArgsConstructor(access = PRIVATE)
public final class WatermarkFilter {

	private final BufferedImage watermarkImg;
	private final AlphaComposite composite;

	public static WatermarkFilter of(Path watermarkFile, float opacity) {
		return new WatermarkFilter(createWatermarkImage(watermarkFile), createComposite(opacity));
	}

	public static WatermarkFilter of(String text, float opacity) {
		return new WatermarkFilter(createWatermarkImage(text), createComposite(opacity));
	}

	public void accept(BufferedImage img) {
		Point p = randPoint(img.getWidth(), img.getHeight());
		var graphics = img.createGraphics();
		try {
			graphics.setComposite(composite);
			graphics.drawImage(watermarkImg, p.x, p.y, null);
		} finally {
			graphics.dispose();
		}
	}

	private Point randPoint(int width, int height) {
		return Position.getRandom().calculate(width, height, watermarkImg.getWidth(), watermarkImg.getHeight());
	}

	@SneakyThrows
	static BufferedImage createWatermarkImage(Path watermarkFile) {
		try (var is = Files.newInputStream(watermarkFile)) {
			return ImageIO.read(is);
		}
	}

	static BufferedImage createWatermarkImage(String text) {
		requireNonNull(text, "Watermark text is null.");
		var font = new Font(SANS_SERIF, ITALIC, 24);
		var bounds = font.getStringBounds(text, new FontRenderContext(new AffineTransform(), true, true));
		int width = (int) bounds.getWidth(), height = (int) bounds.getHeight();
		var image = new BufferedImage(width, height, TYPE_INT_ARGB);

		var graphics = image.createGraphics();
		try {
			graphics.setRenderingHint(KEY_TEXT_ANTIALIASING, VALUE_TEXT_ANTIALIAS_ON);
			int x = 0, y = image.getHeight() + (int) bounds.getCenterY();
			var textLayout = new TextLayout(text, font, graphics.getFontRenderContext());
			graphics.setPaint(LIGHT_GRAY);
			textLayout.draw(graphics, x + 3, y + 3);
			graphics.setPaint(BLACK);
			textLayout.draw(graphics, x, y);
			return image;
		} finally {
			graphics.dispose();
		}
	}

	static AlphaComposite createComposite(float opacity) {
		if (opacity > 1.0f || opacity < 0.0f) {
			throw new IllegalArgumentException("Opacity is out of range of between 0.0f and 1.0f.");
		}
		return AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity);
	}
}
