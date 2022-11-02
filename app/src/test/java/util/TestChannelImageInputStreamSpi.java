package util;

import javax.imageio.spi.ImageInputStreamSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.File;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Locale;

public class TestChannelImageInputStreamSpi extends ImageInputStreamSpi {

	public TestChannelImageInputStreamSpi() {
		super("test", "0", ReadableByteChannel.class);
	}

	@Override
	public String getDescription(Locale locale) {
		return "Service provider for testing, handles problem with file mmap fro Virtual Filesystems";
	}

	@Override
	public ImageInputStream createInputStreamInstance(Object input, boolean useCache, File cacheDir) {
		if (input instanceof ReadableByteChannel in) {
			return new MemoryCacheImageInputStream(Channels.newInputStream(in));
		}
		throw new IllegalArgumentException("Cannot create from " + input.getClass().getName());
	}

}
