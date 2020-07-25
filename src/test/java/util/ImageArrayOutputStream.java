//package util;
//
//import lombok.experimental.Delegate;
//
//import java.io.ByteArrayOutputStream;
//import java.io.OutputStream;
//import java.util.Arrays;
//
//public class ImageArrayOutputStream extends OutputStream {
//	@Delegate(types = OutputStream.class)
//	private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
//
//	@Override
//	public String toString() {
//		return ImageUtil.convertImageToAscii(delegate.toByteArray()).toString();
//	}
//}
