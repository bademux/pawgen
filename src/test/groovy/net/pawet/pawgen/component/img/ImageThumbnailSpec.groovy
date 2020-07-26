package net.pawet.pawgen.component.img

import com.criteo.vips.VipsImage
import com.criteo.vips.enums.VipsImageFormat
import spock.lang.Specification

import javax.imageio.ImageIO
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

import static java.awt.Color.BLACK
import static java.awt.Color.LIGHT_GRAY
import static java.awt.Font.ITALIC
import static java.awt.Font.SANS_SERIF
import static java.awt.RenderingHints.KEY_TEXT_ANTIALIASING
import static java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON
import static java.awt.image.BufferedImage.TYPE_INT_ARGB
import static java.util.Objects.requireNonNull

class ImageThumbnailSpec extends Specification {

	def "AsAttrs"() {
		given:
		// getting file channel
		def bufSrc = new File('/home/nox/workspace/testOutDir/res/img/lidahilfe_banner.jpg').bytes
		VipsImage imageSrc = new VipsImage(bufSrc, bufSrc.length)

		def bufWatermark = new File('/home/nox/workspace/pawgen/src/test/resources/watermark.png').bytes
		VipsImage imageWatermark = new VipsImage(bufWatermark, bufWatermark.length)

		imageSrc.compose(imageWatermark)
		when:
		def res = imageSrc.writeToArray(VipsImageFormat.JPG, true)
		new File("/home/nox/workspace/pawgen/build/result.jpg").withOutputStream {
			it.write(res);
		}
		imageSrc.close()
		imageWatermark.close()
		then:
		true

	}

	def "AsAttrsd"() {
		when:
		def watermark = createWatermarkImage("testText")
		ImageIO.write(watermark, "png", new File("/home/nox/workspace/pawgen/src/test/resources/watermark.png"))
		then:
		true

	}

	static BufferedImage createWatermarkImage(String text) {
		requireNonNull(text, "Watermark text is null.");
		def font = new Font(SANS_SERIF, ITALIC, 24);
		def bounds = font.getStringBounds(text, new FontRenderContext(new AffineTransform(), true, true));
		int width = (int) bounds.getWidth(), height = (int) bounds.getHeight();
		def image = new BufferedImage(width, height, TYPE_INT_ARGB);

		def graphics = image.createGraphics();
		try {
			graphics.setRenderingHint(KEY_TEXT_ANTIALIASING, VALUE_TEXT_ANTIALIAS_ON);
			int x = 0, y = image.getHeight() + (int) bounds.getCenterY();
			def textLayout = new TextLayout(text, font, graphics.getFontRenderContext());
			graphics.setPaint(LIGHT_GRAY);
			textLayout.draw(graphics, x + 3, y + 3);
			graphics.setPaint(BLACK);
			textLayout.draw(graphics, x, y);
			return image;
		} finally {
			graphics.dispose();
		}
	}

}
