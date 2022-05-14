package net.pawet.pawgen.component.xml;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Article;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.resource.ResourceFactory;
import net.pawet.pawgen.component.system.storage.ArticleResource;
import net.pawet.pawgen.component.system.storage.CategoryAwareResource;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoField.*;
import static java.util.Spliterator.*;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;
import static net.pawet.pawgen.component.xml.XmlUtils.getWithPrefix;

@Slf4j
public record ArticleParser(ResourceFactory resourceFactory) {

	public static final Set<String> ROOT_TAG_NAMES = Set.of("article", "gallery");

	@SneakyThrows
	public Stream<Article> parse(CategoryAwareResource readable) {
		var category = readable.getCategory();
		var is = readable.inputStream();
		try {
			var xmlr = PawXMLReader.of(is);
			log.info("Parsing category '{}'", category);
			return xmlEventStream(xmlr)
				.flatMap(xmlEvent -> parse(xmlEvent, readable))
				.onClose(xmlr::close);
		} catch (Exception e) {
			log.error("Can't parse article in '{}' [{}], skipping", category, e.getMessage());
			log.debug("Can't parse article", e);
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

	private Stream<Article> parse(XMLEvent event, CategoryAwareResource resource) {
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

	private Article parse(CategoryAwareResource resource, StartElement startElement, QName elementQName, String name, Attribute attr) {
		Category category = resource.getCategory();
		String title = getTitle(attr);
		var contentParser = new ContentParser((n, attrs) -> resourceFactory.createResource(n, category, attrs));
		var res = ArticleResource.of(resource, title, is -> new ByteArrayInputStream(contentParser.read(is, title).toString().getBytes(UTF_8)));
		QName defQName = getWithPrefix(elementQName, attr.getName());
		return Article.of(res, category, name.trim(), defQName.getPrefix().toLowerCase(), title,
			getAuthor(startElement, defQName),
			getDate(startElement, defQName),
			getSource(startElement, defQName),
			getFile(startElement, defQName));
	}

	static String getTitle(Attribute attr) {
		return ".".equals(attr.getValue()) ? "" : attr.getValue();
	}

	static String getAuthor(StartElement startElement, QName qNameSample) {
		return getTagValueOrDefault(startElement, qNameSample.getNamespaceURI(), "author", qNameSample.getPrefix());
	}

	static Instant getDate(StartElement startElement, QName qNameSample) {
		String date = getTagValueOrDefault(startElement, qNameSample.getNamespaceURI(), "date", qNameSample.getPrefix());
		return parseDate(date);
	}

	private static final DateTimeFormatter DATE_FORMATTER_DDMMYYYY = new DateTimeFormatterBuilder()
		.appendValue(DAY_OF_MONTH, 2)
		.appendLiteral('-')
		.appendValue(MONTH_OF_YEAR, 2)
		.appendLiteral('-')
		.appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
		.toFormatter();

	static Instant parseDate(String date) {
		return Stream.of(DateTimeFormatter.ISO_DATE,
			DATE_FORMATTER_DDMMYYYY
		).map(formatter -> {
			try {
				return formatter.parse(date);
			} catch (Exception e) {
				return null;
			}
		}).filter(Objects::nonNull).findAny()
			.map(LocalDate::from)
			.map(LocalDate::atStartOfDay)
			.map(localDate -> localDate.toInstant(UTC))
			.orElse(Instant.MIN);
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
