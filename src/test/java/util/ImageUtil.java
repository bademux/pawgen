//package util;
//
//import lombok.SneakyThrows;
//import lombok.experimental.Delegate;
//import lombok.experimental.UtilityClass;
//
//import javax.imageio.ImageIO;
//import javax.imageio.stream.FileImageOutputStream;
//import java.awt.*;
//import java.awt.font.FontRenderContext;
//import java.awt.geom.AffineTransform;
//import java.awt.image.BufferedImage;
//import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.File;
//import java.io.OutputStream;
//
//import static java.awt.RenderingHints.KEY_TEXT_ANTIALIASING;
//import static java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
//import static java.awt.image.BufferedImage.TYPE_INT_RGB;
//
//@UtilityClass
//public class ImageUtil {
//
//	@SneakyThrows
//	public BufferedImage createTestImage(String text) {
//		var font = new Font("Dialog", Font.PLAIN, 24);
//		var bounds = font.getStringBounds(text, new FontRenderContext(new AffineTransform(), true, true));
//		var image = new BufferedImage((int) bounds.getWidth(), (int) bounds.getHeight(), TYPE_INT_RGB);
//		{
//			var graphics = (Graphics2D) image.getGraphics();
//			graphics.setFont(font);
//			graphics.setRenderingHint(KEY_TEXT_ANTIALIASING, VALUE_TEXT_ANTIALIAS_ON);
//			graphics.drawString(text, 0, image.getHeight() + (int) bounds.getCenterY());
//		}
//		return image;
//	}
//
//	@SneakyThrows
//	public CharSequence convertImageToAscii(byte[] imageData) {
//		BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageData));
//		StringBuilder sb = new StringBuilder();
//		for (int y = 0; y < img.getHeight(); y++) {
//			for (int x = 0; x < img.getWidth(); x++) {
//				sb.append(img.getRGB(x, y) == 0xff000000 ? ' ' : img.getRGB(x, y) == 0xffffffff ? '#' : '*');
//			}
//			sb.append('\n');
//
//		}
//		return sb;
//	}
//
//}
