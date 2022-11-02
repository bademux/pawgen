package util;

import javax.imageio.spi.ImageOutputStreamSpi;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.io.File;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Locale;

public class TestChannelImageOutputStreamSpi extends ImageOutputStreamSpi {

	public TestChannelImageOutputStreamSpi() {
		super("test", "0", WritableByteChannel.class);
	}

	@Override
	public ImageOutputStream createOutputStreamInstance(Object output, boolean useCache, File cacheDir) {
		if (output instanceof WritableByteChannel out) {
			return new MemoryCacheImageOutputStream(Channels.newOutputStream(out));
		}
		throw new IllegalArgumentException("Cannot create from " + output.getClass().getName());
	}

	@Override
	public String getDescription(Locale locale) {
		return "Service provider for testing, handles problem with file mmap fro Virtual Filesystems";
	}

}
