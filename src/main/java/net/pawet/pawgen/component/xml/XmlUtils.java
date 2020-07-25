package net.pawet.pawgen.component.xml;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import javax.xml.namespace.QName;
import javax.xml.stream.EventFilter;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import java.io.*;

import static java.nio.charset.StandardCharsets.UTF_8;

@UtilityClass
class XmlUtils {

	private static final XMLInputFactory factory = createXmlInputFactory();

	private static XMLInputFactory createXmlInputFactory() {
		XMLInputFactory factory = XMLInputFactory.newInstance();
		factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		return factory;
	}

	@SneakyThrows
	public static XMLEventReader createXMLEventReader(InputStream is) {
		return factory.createXMLEventReader(skipBOM(is));
	}

	private static Reader skipBOM(InputStream is) throws IOException {
		var in = new BufferedReader(new InputStreamReader(is, UTF_8));
		in.mark(1);
		if (in.read() != 0xFEFF) {
			in.reset();
		}
		return in;
	}

	@SneakyThrows
	public static XMLEventReader createFilteredReader(XMLEventReader reader, EventFilter filter) {
		return factory.createFilteredReader(reader, filter);
	}

	public static QName getWithPrefix(QName element, QName attr) {
		return attr.getPrefix().isEmpty() ? element : attr;
	}

}
