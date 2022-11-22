package net.pawet.pawgen.component.xml;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.utils.StringBuilderWriter;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.nio.channels.ReadableByteChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.StreamSupport.stream;

@Slf4j
@RequiredArgsConstructor
public final class ContentParser {

	private final BiFunction<String, Map<String, String>, Map<String, String>> handleResource;

	@SneakyThrows
	public CharSequence read(ReadableByteChannel in) {
		try (var xmlReader = XmlReader.of(handleResource, in)) {
			return xmlReader.read();
		}
	}

}

@RequiredArgsConstructor
class XmlReader implements AutoCloseable {

	private final ArticleContentBody body;

	@SneakyThrows
	public static XmlReader of(BiFunction<String, Map<String, String>, Map<String, String>> handleResource, ReadableByteChannel is) {
		var body = findBody(handleResource, PawXMLEventReader.of(is));
		return new XmlReader(body);
	}

	public static ArticleContentBody findBody(BiFunction<String, Map<String, String>, Map<String, String>> handleResource, XMLEventReader xmlr) throws XMLStreamException {
		while (xmlr.hasNext()) {
			XMLEvent event = xmlr.nextEvent();
			if (!event.isStartElement()) {
				continue;
			}
			StartElement startElement = event.asStartElement();
			QName articleQName = startElement.getName();
			if (!"body".contains(articleQName.getLocalPart())) {
				continue;
			}
			Iterator<Attribute> attrsIt = startElement.getAttributes();
			QName titleAttrName = getTitleAttrName(attrsIt);
			if (titleAttrName == null) {
				continue;
			}
			return ArticleContentBody.of(articleQName, xmlr, handleResource);
		}
		throw new IllegalArgumentException("No article with attribute title found found");
	}

	private static QName getTitleAttrName(Iterator<Attribute> attrsIt) {
		while (attrsIt.hasNext()) {
			Attribute attr = attrsIt.next();
			QName name = attr.getName();
			if ("title".equalsIgnoreCase(name.getLocalPart())) {
				return name;
			}
		}
		return null;
	}

	@SneakyThrows
	public CharSequence read() {
		StringBuilder sb = new StringBuilder();
		body.read(sb);
		return sb;
	}

	@SneakyThrows
	public void close() {
		body.close();
	}

}

record ArticleContentBody(QName rootTag,
						  Function<Iterator<Attribute>, Stream<Attribute>> filter,
						  @Delegate(types = AutoCloseable.class) XMLEventReader xmlr,
						  BiFunction<String, Map<String, String>, Map<String, String>> handleResource) implements AutoCloseable {

	public static ArticleContentBody of(QName rootTag, XMLEventReader xmlr, BiFunction<String, Map<String, String>, Map<String, String>> handleResource) {
		return new ArticleContentBody(rootTag, itr -> stream(spliteratorUnknownSize(itr, 0), false), xmlr, handleResource);
	}

	@SneakyThrows
	public void read(StringBuilder sb) {
		while (xmlr.hasNext()) {
			XMLEvent event = xmlr.nextEvent();
			if (event.isEndElement() && rootTag.equals(event.asEndElement().getName())) {
				return;
			}
			handleTag(sb, event);
		}
	}

	private void handleTag(StringBuilder sb, XMLEvent event) throws XMLStreamException {
		switch (event.getEventType()) {
			case XMLStreamConstants.START_ELEMENT -> {
				StartElement startElement = event.asStartElement();
				QName qName = startElement.getName();
				String name = qName.getLocalPart();
				sb.append('<').append(name);

				var attributes = filter.apply(startElement.getAttributes())
					.filter(attr -> attr.getValue() != null)
					.filter(attr -> !attr.getValue().isEmpty())
					.collect(toMap(
						attr -> attr.getName().getLocalPart(), Attribute::getValue,
						(s1, s2) -> Optional.of(s1).filter(not(String::isBlank)).orElse(s2)
					));

				handleResource.apply(name, attributes)
					.forEach((key, value) -> appendAttribute(sb, key, value));

				if (isEmptyTag(name)) {
					sb.append('/');
				}
				sb.append('>');
			}
			case XMLStreamConstants.END_ELEMENT -> {
				String name = event.asEndElement().getName().getLocalPart();
				if (!isEmptyTag(name)) {
					sb.append("</").append(name).append('>');
				}
			}
			case XMLStreamConstants.CHARACTERS -> {
				Characters characters = event.asCharacters();
				if (!characters.isIgnorableWhiteSpace()) {
					characters.writeAsEncodedUnicode(new StringBuilderWriter(sb));
				}
			}
		}
	}

	@SneakyThrows
	private static Appendable appendAttribute(Appendable sb, String key, String value) {
		return sb.append(' ').append(key).append("=\"").append(value).append('"');
	}

	private boolean isEmptyTag(String name) {
		return "img".equalsIgnoreCase(name) || "hr".equalsIgnoreCase(name) || "br".equalsIgnoreCase(name);
	}

}
