package net.pawet.pawgen.component.xml;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Article;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.system.storage.ArticleResource;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import static java.util.function.Predicate.not;
import static java.util.stream.StreamSupport.stream;

@Slf4j
@RequiredArgsConstructor(staticName = "of")
public final class XmlArticleParser {

	private final BiFunction<Category, Map<String, String>, Map<String, String>> imageResourceProcessor;
	private final BiFunction<Category, Map<String, String>, Map<String, String>> linkResourceProcessor;

	@SneakyThrows
	public Article parse(ArticleResource readable) {
		var category = readable.getCategory();
		log.info("Parsing category '{}'", category);
		try (var xmlEventStream = PawXMLEventReader.of(readable.readable())) {
			return parse(getRootElement(xmlEventStream), readable);
		}
	}

	private static StartElement getRootElement(PawXMLEventReader xmlEventStream) throws XMLStreamException {
		while (xmlEventStream.hasNext()) {
			var event = xmlEventStream.nextEvent();
			if (!event.isStartElement()) {
				continue;
			}
			var startElement = event.asStartElement();
			if ("body".contains(startElement.getName().getLocalPart())) {
				return startElement;
			}
		}
		throw new IllegalStateException("Can't find root element; last event: " + xmlEventStream);
	}

	private Article parse(StartElement startElement, ArticleResource resource) {
		var attrs = stream(((Iterable<Attribute>) startElement::getAttributes).spliterator(), false).toList();
		String lang = resource.getLanguage();
		var title = attrs.stream()
			.filter(attr1 -> "title".equalsIgnoreCase(attr1.getName().getLocalPart()))
			.findFirst()
			.map(Attribute::getValue)
			.map(s -> ".".equals(s) ? "" : s)
			.orElseThrow();
		var aliases = attrs.stream()
			.filter(attr1 -> "alias".equalsIgnoreCase(attr1.getName().getLocalPart()))
			.map(Attribute::getValue)
			.filter(Objects::nonNull)
			.filter(not(String::isBlank))
			.distinct()
			.toList();
		String author = attrs.stream()
			.filter(attr1 -> "author".equalsIgnoreCase(attr1.getName().getLocalPart()))
			.findFirst()
			.map(Attribute::getValue)
			.filter(not(String::isBlank))
			.orElse(null);
		String source = attrs.stream()
			.filter(attr1 -> "source".equalsIgnoreCase(attr1.getName().getLocalPart()))
			.findFirst()
			.map(Attribute::getValue)
			.filter(not(String::isBlank))
			.orElse(null);
		ZonedDateTime date = attrs.stream()
			.filter(attr1 -> "date".equalsIgnoreCase(attr1.getName().getLocalPart()))
			.findFirst()
			.map(Attribute::getValue)
			.filter(not(String::isBlank))
			.map(XmlArticleParser::parseDate)
			.orElse(null);
		String file = attrs.stream()
			.filter(attr1 -> "file".equalsIgnoreCase(attr1.getName().getLocalPart()))
			.findFirst()
			.map(Attribute::getValue)
			.filter(not(String::isBlank))
			.orElse(null);
		String type = attrs.stream()
			.filter(attr1 -> "type".equalsIgnoreCase(attr1.getName().getLocalPart()))
			.findFirst()
			.map(Attribute::getValue)
			.filter(not(String::isBlank))
			.orElse("article");
		Category category = resource.getCategory();
		var contentParser = new ContentParser((t, attrs1) -> switch (t) {
			case "img" -> imageResourceProcessor.apply(category, attrs1);
			case "a" -> linkResourceProcessor.apply(category, attrs1);
			default -> attrs1;
		}
		);
		return Article.of(resource, () -> contentParser.read(resource.readable()),
			type, lang, title,
			author, date, source,
			file,
			aliases
		);
	}

	///2018-04-22T07:13:30Z
	private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
		.appendPattern("[dd-MM-yyyy][yyyy-MM-dd][dd.MM.yyyy][yyyy.MM.dd]['T'][ ][HH:mm:ss][HH:mm][X]")
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

}
