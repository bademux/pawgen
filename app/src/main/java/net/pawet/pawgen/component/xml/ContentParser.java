package net.pawet.pawgen.component.xml;

import lombok.NonNull;
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
import java.io.IOException;
import java.io.Writer;
import java.nio.CharBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static net.pawet.pawgen.component.xml.ContentParser.ROOT_TAG_NAMES;
import static net.pawet.pawgen.component.xml.XmlUtils.getWithPrefix;

@Slf4j
record ContentParser(BiFunction<String, Map<String, String>, Map<String, String>> handleResource) {

	public static final Set<String> ROOT_TAG_NAMES = Set.of("article", "gallery");

	@SneakyThrows
	public CharSequence read(ReadableByteChannel in, String title) {
		try (var xmlReader = XmlReader.of(handleResource, in, title)) {
			return xmlReader.read();
		}
	}

}

@RequiredArgsConstructor
class XmlReader implements AutoCloseable {

	private final ArticleContentBody body;

	@SneakyThrows
	public static XmlReader of(BiFunction<String, Map<String, String>, Map<String, String>> handleResource, ReadableByteChannel is, String title) {
		var body = findBody(handleResource, PawXMLEventReader.of(is), title);
		return new XmlReader(body);
	}

	public static ArticleContentBody findBody(BiFunction<String, Map<String, String>, Map<String, String>> handleResource,
											  XMLEventReader xmlr, String title) throws XMLStreamException {
		while (xmlr.hasNext()) {
			XMLEvent event = xmlr.nextEvent();
			if (!event.isStartElement()) {
				continue;
			}
			StartElement startElement = event.asStartElement();
			QName articleQName = startElement.getName();
			if (!ROOT_TAG_NAMES.contains(articleQName.getLocalPart())) {
				continue;
			}
			Iterator<Attribute> attrsIt = startElement.getAttributes();
			QName titleAttrName = getTitleAttrName(attrsIt, title);
			if (titleAttrName == null) {
				continue;
			}
			String lang = getWithPrefix(articleQName, titleAttrName).getPrefix();
			return ArticleContentBody.of(articleQName, lang, xmlr, handleResource);
		}
		throw new IllegalArgumentException("No article named: '" + title + "' found");
	}

	private static QName getTitleAttrName(Iterator<Attribute> attrsIt, String title) {
		while (attrsIt.hasNext()) {
			Attribute attr = attrsIt.next();
			QName name = attr.getName();
			if ("title".equalsIgnoreCase(name.getLocalPart()) && title.equals(attr.getValue())) {
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

	public static ArticleContentBody of(QName rootTag, String lang, XMLEventReader xmlr, BiFunction<String, Map<String, String>, Map<String, String>> handleResource) {
		var filter = new PawFilter(lang);
		var xmlFilteredReader = XmlUtils.createFilteredReader(xmlr, filter);
		return new ArticleContentBody(rootTag, filter::filterAttributes, xmlFilteredReader, handleResource);
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

	private void handleTag(StringBuilder sb, XMLEvent event) throws XMLStreamException, IOException {
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
