package net.pawet.pawgen.component.system.storage;

import lombok.SneakyThrows;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.FileAlreadyExistsException;

import static org.slf4j.LoggerFactory.getLogger;

public interface Resource extends ReadableResource, WritableResource {

	int TRANSFER_SIZE = 8192;

	ReadableByteChannel readable();

	WritableByteChannel writable();

	@SneakyThrows
	default void transfer() {
		try (var in = readable(); var out = writable()) {
			if(in instanceof FileChannel f){
				f.transferTo(0, Long.MAX_VALUE, out);
				return;
			}
			transfer(in, out);
		} catch (FileAlreadyExistsException e) {
			getLogger(Resource.class).trace("Already transferred: {}", e.getFile());
		}
	}

	private void transfer(ReadableByteChannel source, WritableByteChannel target) throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(TRANSFER_SIZE);
		long tw = 0;
		try {
			while (tw < TRANSFER_SIZE) {
				bb.limit((int)Math.min(Long.MAX_VALUE - tw, TRANSFER_SIZE));
				int nr = source.read(bb);
				if (nr <= 0) {
					break;
				}
				bb.flip();
				int nw = target.write(bb);
				tw += nw;
				if (nw != nr) {
					break;
				}
				bb.clear();
			}
		} catch (IOException x) {
			if (tw > 0) {
				return;
			}
			throw x;
		}
	}

	Resource EMPTY = new Resource() {
		@Override
		public ReadableByteChannel readable() {
			return Channels.newChannel(InputStream.nullInputStream());
		}

		@Override
		public WritableByteChannel writable() {
			return Channels.newChannel(OutputStream.nullOutputStream()) ;
		}
	};

}
