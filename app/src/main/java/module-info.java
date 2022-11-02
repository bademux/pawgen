module pawgen {
	exports net.pawet.pawgen;
	exports net.pawet.pawgen.component;
	exports net.pawet.pawgen.utils;

	requires static lombok;
	requires java.xml;
	requires jdk.zipfs;
	requires com.github.mustachejava;
	requires java.desktop;
	requires java.net.http;
	requires jakarta.json;
	requires java.logging;
	requires org.slf4j;
	requires org.slf4j.jul;
	requires org.glassfish.jakarta.json;
	uses jakarta.json.spi.JsonProvider;

	requires imageio;
	uses javax.imageio.spi.ImageInputStreamSpi;
	uses javax.imageio.spi.ImageOutputStreamSpi;
	uses javax.imageio.spi.ImageWriterSpi;
	uses javax.imageio.spi.ImageReaderSpi;

}
