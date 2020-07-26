package net.pawet.pawgen.component.xml;

import lombok.SneakyThrows;
import lombok.extern.java.Log;
import net.pawet.pawgen.component.ArticleHeader;
import net.pawet.pawgen.component.Storage;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.util.Arrays;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;
import static net.pawet.pawgen.component.xml.XmlUtils.createXMLEventReader;
import static net.pawet.pawgen.component.xml.XmlUtils.getWithPrefix;

@Log
public class HeaderParser {

	public static final Set<String> ROOT_TAG_NAMES = Set.of("article", "gallery");

	@SneakyThrows
	public Stream<ArticleHeader> parse(Storage.Readable readable) {
		try (var is = readable.readContent()) {
			var xmlr = createXMLEventReader(is);
			try {
				log.log(Level.FINE, () -> "Handling category " + Arrays.toString(readable.getCategory()));
				return parse(readable, xmlr);
			} finally {
				xmlr.close();
			}
		} catch (Exception e) {
			log.log(Level.WARNING, e, () -> "error while parsing " + readable);
			throw e;
		}
	}

	private Stream<ArticleHeader> parse(Storage.Readable readable, XMLEventReader xmlr) throws XMLStreamException {
		var articleHeaders = Stream.<ArticleHeader>builder();
		while (xmlr.hasNext()) {
			XMLEvent event = xmlr.nextEvent();
			if (!event.isStartElement()) {
				continue;
			}
			StartElement startElement = event.asStartElement();
			QName elementQName = startElement.getName();
			String name = elementQName.getLocalPart();
			if (!ROOT_TAG_NAMES.contains(name)) {
				continue;
			}
			stream(((Iterable<Attribute>) startElement::getAttributes).spliterator(), false)
				.filter(attr -> "title".equalsIgnoreCase(attr.getName().getLocalPart()))
				.map(attr -> {
					QName defQName = getWithPrefix(elementQName, attr.getName());
					return ArticleHeader.of(readable.getCategory(), name.trim(), defQName.getPrefix().toLowerCase(),
						attr.getValue(),
						getAuthor(startElement, defQName),
						getDate(startElement, defQName),
						getSource(startElement, defQName),
						getFile(startElement, defQName),
						readable.getLastModifiedTime()
					);
				})
				.forEach(articleHeaders);
		}
		return articleHeaders.build();
	}

	private String getAuthor(StartElement startElement, QName qNameSample) {
		return getTagValueOrDefault(startElement, qNameSample.getNamespaceURI(), "author", qNameSample.getPrefix());
	}

	private String getDate(StartElement startElement, QName qNameSample) {
		return getTagValueOrDefault(startElement, qNameSample.getNamespaceURI(), "date", qNameSample.getPrefix());
	}

	private String getSource(StartElement startElement, QName qNameSample) {
		return getTagValueOrDefault(startElement, qNameSample.getNamespaceURI(), "source", qNameSample.getPrefix());
	}

	private String getFile(StartElement startElement, QName qNameSample) {
		return getTagValueOrDefault(startElement, qNameSample.getNamespaceURI(), "file", qNameSample.getPrefix());
	}

	private String getTagValueOrDefault(StartElement startElement, String namespaceURI, String localPart, String prefix) {
		var attribute = startElement.getAttributeByName(new QName(namespaceURI, localPart, prefix));
		if (attribute != null) {
			return attribute.getValue();
		}
		attribute = startElement.getAttributeByName(new QName(localPart));
		if (attribute != null) {
			return attribute.getValue();
		}
		return null;
	}

}
