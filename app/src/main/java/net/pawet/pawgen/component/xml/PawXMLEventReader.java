package net.pawet.pawgen.component.xml;

import lombok.SneakyThrows;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.util.EventReaderDelegate;
import java.io.IOException;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class PawXMLEventReader extends EventReaderDelegate implements AutoCloseable {

	private final AutoCloseable closeable;

	@SneakyThrows
	public static PawXMLEventReader of(ReadableByteChannel in) {
		return new PawXMLEventReader(Channels.newReader(in, UTF_8));
	}

	PawXMLEventReader(Reader reader) throws XMLStreamException, IOException {
		super(XmlUtils.createXMLEventReader(reader));
		this.closeable = reader;
	}

	@SneakyThrows
	@Override
	public void close() {
		try {
			super.close();
		} finally {
			closeable.close();
		}
	}

}
