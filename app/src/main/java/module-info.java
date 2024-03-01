module pawgen {
	requires static lombok;

	exports net.pawet.pawgen;
	exports net.pawet.pawgen.component;
	exports net.pawet.pawgen.utils;

    requires deployer;
	requires jdk.zipfs;
	requires java.xml;
	requires java.desktop;
	requires java.net.http;
	requires java.logging;
	requires org.slf4j;
	requires org.slf4j.jul;
	requires com.github.mustachejava;
	requires com.github.jai_imageio;
	requires org.eclipse.parsson;
	requires commonmark;
	requires flexmark;

	uses jakarta.json.spi.JsonProvider;
	uses javax.imageio.spi.ImageInputStreamSpi;
	uses javax.imageio.spi.ImageOutputStreamSpi;
	uses javax.imageio.spi.ImageWriterSpi;
	uses javax.imageio.spi.ImageReaderSpi;

}
