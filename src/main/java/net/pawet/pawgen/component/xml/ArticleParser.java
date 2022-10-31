package net.pawet.pawgen.component.xml;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Article;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.resource.ResourceFactory;
import net.pawet.pawgen.component.system.storage.ArticleResourceWrapper;
import net.pawet.pawgen.component.system.storage.ArticleResource;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Spliterator.*;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;
import static net.pawet.pawgen.component.xml.XmlUtils.getWithPrefix;

@Slf4j
public record ArticleParser(ResourceFactory resourceFactory) {

	public static final Set<String> ROOT_TAG_NAMES = Set.of("article", "gallery");

	@SneakyThrows
	public Stream<Article> parse(ArticleResource readable) {
		var category = readable.getCategory();
		var is = readable.readable();
		try {
			var xmlr = PawXMLEventReader.of(is);
			log.info("Parsing category '{}'", category);
			return xmlEventStream(xmlr)
				.flatMap(xmlEvent -> parse(xmlEvent, readable))
				.onClose(xmlr::close);
		} catch (Exception e) {
			log.error("Can't parse article in '{}', skipping", category, e);
			return Stream.empty();
		}
	}

	private Stream<XMLEvent> xmlEventStream(XMLEventReader xmlr) {
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

		return stream(spliteratorUnknownSize(iterator, ORDERED | IMMUTABLE | NONNULL), false);
	}

	private Stream<Article> parse(XMLEvent event, ArticleResource resource) {
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
			.map(attr -> parse(resource, startElement, elementQName, name, attr));
	}

	private Article parse(ArticleResource resource, StartElement startElement, QName elementQName, String name, Attribute attr) {
		Category category = resource.getCategory();
		String title = getTitle(attr);
		var contentParser = new ContentParser((n, attrs) -> resourceFactory.createResource(n, category, attrs));
		QName defQName = getWithPrefix(elementQName, attr.getName());
		String file = getFile(startElement, defQName);
		var res = ArticleResourceWrapper.of(resource, title, file, () -> contentParser.read(resource.readable(), title));
		return Article.of(res, name.trim(), defQName.getPrefix().toLowerCase(), title,
			getAuthor(startElement, defQName),
			getDate(startElement, defQName),
			getSource(startElement, defQName));
	}

	static String getTitle(Attribute attr) {
		return ".".equals(attr.getValue()) ? "" : attr.getValue();
	}

	static String getAuthor(StartElement startElement, QName qNameSample) {
		return getTagValueOrDefault(startElement, qNameSample.getNamespaceURI(), "author", qNameSample.getPrefix());
	}

	static ZonedDateTime getDate(StartElement startElement, QName qNameSample) {
		return parseDate(getTagValueOrDefault(startElement, qNameSample.getNamespaceURI(), "date", qNameSample.getPrefix()));
	}

	private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
		.appendPattern("[dd-MM-yyyy][yyyy-MM-dd][dd.MM.yyyy][yyyy.MM.dd]['T'][ ][HH:mm][X]")
		.parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
		.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
		.toFormatter()
		.withZone(ZoneOffset.UTC);

	static ZonedDateTime parseDate(String date) {
		if (date != null) {
			try {
				return DATE_FORMATTER.parse(date, ZonedDateTime::from);
			} catch (Exception e) {
				log.error("Can't parse date '{}'", date, e);
			}
		}
		return null;
	}

	static String getSource(StartElement startElement, QName qNameSample) {
		return getTagValueOrDefault(startElement, qNameSample.getNamespaceURI(), "source", qNameSample.getPrefix());
	}

	static String getFile(StartElement startElement, QName qNameSample) {
		String file = getTagValueOrDefault(startElement, qNameSample.getNamespaceURI(), "file", qNameSample.getPrefix());
		return file == null || file.isBlank() ? null : "/files/" + file;
	}

	static String getTagValueOrDefault(StartElement startElement, String namespaceURI, String localPart, String prefix) {
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
