package net.pawet.pawgen.component.xml;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.pempek.unicode.UnicodeBOMInputStream;

import javax.xml.namespace.QName;
import javax.xml.stream.EventFilter;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.READ;

@UtilityClass
class XmlUtils {

	private static final XMLInputFactory factory = createXmlInputFactory();

	private static XMLInputFactory createXmlInputFactory() {
		XMLInputFactory factory = XMLInputFactory.newInstance();
		factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		return factory;
	}

	@SneakyThrows
	public static XMLEventReader createXMLEventReader(InputStream inputStream) {
		return createXMLEventReaderRaw(new UnicodeBOMInputStream(new BufferedInputStream(inputStream)).skipBOM());
	}

	@SneakyThrows
	static XMLEventReader createXMLEventReaderRaw(InputStream inputStream) {
		return factory.createXMLEventReader(inputStream);
	}

	@SneakyThrows
	public static XMLEventReader createFilteredReader(XMLEventReader reader, EventFilter filter) {
		return factory.createFilteredReader(reader, filter);
	}

	public static QName getWithPrefix(QName element, QName attr) {
		return attr.getPrefix().isEmpty() ? element : attr;
	}

}
