package net.pawet.pawgen.component.resource.img
//package net.pawet.pawgen.component.resource.img
//
//import net.coobird.thumbnailator.filters.Watermark
//import spock.lang.Specification
//import util.ImageArrayOutputStream
//import util.ImageUtil
//
//import javax.imageio.ImageIO
//import java.util.function.Function
//
//import static net.pawet.pawgen.component.resource.img.Position.Positions.TOP_LEFT
//
//class ImageThumbnailTest extends Specification {
//	def "AsAttrs"() {
//	}
//
//	def "ProcessImage"() {
//		given:
//		def img = ImageUtil.createTestImage("Test")
//		def imgData = new ByteArrayOutputStream().withCloseable {
//			ImageIO.write(img, "bmp", it)
//			return it.toByteArray()
//		}
//		def image = new ImageThumbnail(img.getWidth(), img.getHeight(), [src: 'test'], createWatermark(), 70, "bmp")
//		and:
//		def os = [new ImageArrayOutputStream(), new ImageArrayOutputStream()]
//		def osIt = os.iterator()
//		when:
//		image.processImage({ new ByteArrayInputStream(imgData) } as Function, { osIt.next() } as Function)
//		then:
//		verifyAll(os) {
//			get(0).toString() == '''
//  * ** ** *  * ** *  * *  **  *   * **   * *  * * *
//**  **  **** ***  ***   **  **  *  *  ** ***** ** *
// ****  ****  *#***    *   **  ** * ***  ** *#     *
//*    * ***  **** ***  ****       **   **  *##  ***\u0020
//  ** * **** *** *    **  *  ** ** ** ** *  #* *  **
//*  * ***#** *#** *  *****  *  ** *  ** * * #*** * *
//  **##*#*#*#**#*#** ********  ********* **##***#**\u0020
// * ***###*#*##*#*# **####** * **#**#**#* #***#*## *
// *  * *#** *****  * *****#*** *********  * ## *\u0020\u0020\u0020\u0020
// *  ***** ****  *  **  ***** **#*** *  * * ## *\u0020\u0020\u0020\u0020
//  ** **#** ****   *****  **** **** ****  * ## *\u0020\u0020\u0020\u0020
//*    ********* **** *#*###** ***#****  * * ## *\u0020\u0020\u0020\u0020
// **##*##***###**** ****#####*  **#**#*** * ## *\u0020\u0020\u0020\u0020
// *##*#**####**##* * ***     ** ******##* * ## *\u0020\u0020\u0020\u0020
// * * *#*  *#**   ** *  *  **  *   *  *** * ## *\u0020\u0020\u0020\u0020
//*  * *#* ****  **  ** * **  * *  * * *#* * ## *\u0020\u0020\u0020\u0020
//  *****   ***  **   ******** *********#*  **#**   *
//**  **** ******   ****#*##** ***#**###**** ***#*#\u0020\u0020
//   **** ****  ** ** ********* *********    ****** *
//***  * *  * *** *    **  *  ***    **  * *  ***** *
//    *** ** ** **   *  *    **  ***   * *  **** * *\u0020
// *  * *      * * ** **  *  * *   ****  *** * * **\u0020\u0020
// * **  **    *   ** *     * * ***            **   *
//*  * **  * ** * *  * **  * * * **  *  **  *** * ***
//''' + '\n' + ' '.repeat(52) + '\n' + ' '.repeat(52)
//			get(1).toString().trim() == '''
//       *#*  *#*
//        *#*  *#*                           ##
//       ***   ***                           ##
//       *#*  ***                            ##
//       *#*  *#*                            ##
//   *##############  ******     ********  ########
//   *##############  #####**   **#######  ########
//      *#*  ***      ********  *#*******    ##
//      *#*  *#*           *#*  *#*          ##
//      *#*  *#*           *#*  *#**         ##
//      ***  *#*      *****##*  *##*****     ##
//  ##############*   ########   **####***   ##
//  ##############*                ****##*   ##
//     *#*  *#*                        *#*   ##
//     ***  *#*                        *#*   *#*
//    ***   ***       *******   ********#*   *#**
//    *#*  ***        #######   ########**   *#####
//    *#*  *#*        *******   *********    ****##
//'''.trim()
//		}
//	}
//
//	private static Watermark createWatermark() {
//		return new Watermark(TOP_LEFT, ImageUtil.createTestImage("#"), 1f)
//	}
//
//}
