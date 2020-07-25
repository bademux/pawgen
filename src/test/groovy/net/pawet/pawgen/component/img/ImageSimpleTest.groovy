//package net.pawet.pawgen.component.img
//
//import spock.lang.Specification
//import util.ImageArrayOutputStream
//import util.ImageUtil
//
//import javax.imageio.ImageIO
//import java.util.function.Function
//
//class ImageSimpleTest extends Specification {
//	def "AsAttrs"() {
//	}
//
//	def "ProcessImage"() {
//		given:
//		def img = ImageUtil.createTestImage("Test image");
//		def imgData = new ByteArrayOutputStream().withCloseable {
//			ImageIO.write(img, "bmp", it)
//			return it.toByteArray()
//		}
//		def image = new ImageSimple(img.getWidth(), img.getHeight(), [src: 'test'])
//		def os = new ImageArrayOutputStream()
//		when:
//		image.processImage({ new ByteArrayInputStream(imgData) } as Function, { os } as Function)
//		then:
//		os.hasTheSameDataAs(imgData)
//	}
//
//}
