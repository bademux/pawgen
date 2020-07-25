package net.pawet.pawgen.component.xml;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.ArticleHeader;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.system.storage.CategoryAwareResource;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.time.Instant;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Spliterator.*;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;
import static net.pawet.pawgen.component.xml.XmlUtils.createXMLEventReader;
import static net.pawet.pawgen.component.xml.XmlUtils.getWithPrefix;

@Slf4j
public class HeaderParser {

	public static final Set<String> ROOT_TAG_NAMES = Set.of("article", "gallery");

	@SneakyThrows
	public Stream<ArticleHeader> parse(CategoryAwareResource readable) {
		var category = readable.getCategory();
		var is = readable.inputStream();
		try {
			var xmlr = createXMLEventReader(is);
			log.info("Parsing category '{}'", category);
			var iterator = new Iterator<XMLEvent>() {
				@Override
				public boolean hasNext() {
					return xmlr.hasNext();
				}

				@SneakyThrows
				@Override
				public XMLEvent next() {
					return xmlr.nextEvent();
				}
			};

			return stream(spliteratorUnknownSize(iterator, ORDERED | IMMUTABLE | NONNULL), false)
				.flatMap(xmlEvent -> parse(xmlEvent, category, readable.getLastModifiedTime()))
				.onClose(() -> safeClose(xmlr))
				.onClose(() -> safeClose(is));
		} catch (Exception e) {
			log.error("Can't parse article in '{}' [{}], skipping", category, e.getMessage());
			log.debug("Can't parse article", e);
			safeClose(is);
			return Stream.empty();
		}
	}

	private void safeClose(AutoCloseable autoCloseable) {
		try {
			autoCloseable.close();
		} catch (Exception ignore) {
		}
	}

	private void safeClose(XMLEventReader xmlr) {
		try {
			xmlr.close();
		} catch (XMLStreamException ignore) {
		}
	}

	private Stream<ArticleHeader> parse(XMLEvent event, Category category, Instant lastModifiedTime) {
		if (!event.isStartElement()) {
			return Stream.empty();
		}
		StartElement startElement = event.asStartElement();
		QName elementQName = startElement.getName();
		String name = elementQName.getLocalPart();
		if (!ROOT_TAG_NAMES.contains(name)) {
			return Stream.empty();
		}
		return stream(spliteratorUnknownSize(startElement.getAttributes(), ORDERED | IMMUTABLE | NONNULL), false)
			.filter(attr -> "title".equalsIgnoreCase(attr.getName().getLocalPart()))
			.map(attr -> {
				QName defQName = getWithPrefix(elementQName, attr.getName());
				return ArticleHeader.of(category, name.trim(), defQName.getPrefix().toLowerCase(),
					".".equals(attr.getValue()) ? "" : attr.getValue(),
					getAuthor(startElement, defQName),
					getDate(startElement, defQName),
					getSource(startElement, defQName),
					getFile(startElement, defQName),
					lastModifiedTime
				);
			});
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
		String file = getTagValueOrDefault(startElement, qNameSample.getNamespaceURI(), "file", qNameSample.getPrefix());
		return file == null || file.isBlank() ? null : "/files/" + file;
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
