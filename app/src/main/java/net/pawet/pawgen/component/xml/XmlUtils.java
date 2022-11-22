package net.pawet.pawgen.component.xml;

import lombok.experimental.UtilityClass;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

@UtilityClass
class XmlUtils {

	private static final XMLInputFactory factory = createXmlInputFactory();

	static XMLInputFactory createXmlInputFactory() {
		XMLInputFactory factory = XMLInputFactory.newInstance();
		factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		return factory;
	}

	static XMLEventReader createXMLEventReader(Reader reader) throws IOException, XMLStreamException {
		return factory.createXMLEventReader(skipBOM(reader));
	}

	static Reader skipBOM(Reader reader) throws IOException {
		var in = new BufferedReader(reader);
		in.mark(1);
		if (in.read() != 0xFEFF) {
			in.reset();
		}
		return in;
	}

}

