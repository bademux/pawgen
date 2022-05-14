package net.pawet.pawgen.component.xml;

import lombok.SneakyThrows;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.util.EventReaderDelegate;
import java.io.InputStream;

public final class PawXMLReader extends EventReaderDelegate implements AutoCloseable {

	private final AutoCloseable is;

	@SneakyThrows
	public static PawXMLReader of(InputStream is) {
		return new PawXMLReader(XmlUtils.createXMLEventReader(is), is);
	}

	PawXMLReader(XMLEventReader delegate, InputStream is) {
		super(delegate);
		this.is = is;
	}

	@SneakyThrows
	@Override
	public void close() {
		try {
			super.close();
		} finally {
			is.close();
		}
	}

}
