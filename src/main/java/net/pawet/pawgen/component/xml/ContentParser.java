package net.pawet.pawgen.component.xml;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import net.pawet.pawgen.component.ArticleHeader;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.Storage;
import net.pawet.pawgen.component.img.ImageParser;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.Writer;
import java.util.*;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toMap;
import static net.pawet.pawgen.component.xml.XmlUtils.createXMLEventReader;
import static net.pawet.pawgen.component.xml.XmlUtils.getWithPrefix;

@Log
@RequiredArgsConstructor(staticName = "of")
public class ContentParser {

	public static final Set<String> ROOT_TAG_NAMES = Set.of("article", "gallery");

	private final Storage storage;
	private final ImageParser imageParser;

	public CharSequence read(ArticleHeader header) {
		return read(header.getCategory(), header.getTitle());
	}

	@SneakyThrows
	CharSequence read(Category category, String title) {
		XMLEventReader xmlr = null;
		try (var inputStream = storage.inputStream(category, "index.xml")) {
			xmlr = createXMLEventReader(inputStream);
			return new XmlArticle(category, title).read(xmlr);
		} finally {
			if (xmlr != null) {
				xmlr.close();
			}
		}
	}

	@RequiredArgsConstructor
	private final class XmlArticle {

		private final Category category;
		private final String title;

		private CharSequence read(XMLEventReader xmlr) throws XMLStreamException {
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
				QName titleAttrName = getTitleAttrName(attrsIt);
				if (titleAttrName == null) {
					continue;
				}
				String lang = getWithPrefix(articleQName, titleAttrName).getPrefix();
				return read(xmlr, articleQName, lang);
			}
			throw new IllegalArgumentException("No article named: '" + title + "' found for category " + category);
		}

		private QName getTitleAttrName(Iterator<Attribute> attrsIt) {
			while (attrsIt.hasNext()) {
				Attribute attr = attrsIt.next();
				QName name = attr.getName();
				if ("title".equalsIgnoreCase(name.getLocalPart()) && title.equals(attr.getValue())) {
					return name;
				}
			}
			return null;
		}

		private CharSequence read(XMLEventReader xmlr, QName rootTag, String lang) throws XMLStreamException {
			PawFilter filter = new PawFilter(lang);
			var xmlFilteredReader = XmlUtils.createFilteredReader(xmlr, filter);
			var builder = new StringBuilder(512);
			while (xmlFilteredReader.hasNext()) {
				XMLEvent event = xmlFilteredReader.nextEvent();
				if (event.isEndElement() && rootTag.equals(event.asEndElement().getName())) {
					break;
				}
				handleTag(builder, event, filter);
			}
			return builder;
		}

		@SneakyThrows
		private void handleTag(StringBuilder sb, XMLEvent event, PawFilter filter) {
			switch (event.getEventType()) {
				case XMLStreamConstants.START_ELEMENT: {
					StartElement startElement = event.asStartElement();
					QName qName = startElement.getName();
					String name = qName.getLocalPart();
					sb.append('<').append(name);

					Map<String, String> attributes = filter.filterAttributes(startElement::getAttributes)
						.collect(toMap(attr -> attr.getName().getLocalPart(), Attribute::getValue, (s, s2) -> Optional.of(s).filter(Predicate.not(String::isBlank)).orElse(s2)));
					if ("img".equals(name)) {
						var attrs = new HashMap<>(imageParser.createImgAttributes(attributes, category));
						attributes.forEach(attrs::putIfAbsent);
						attributes = attrs;
					}

					attributes
						.forEach((key, value) -> sb.append(' ').append(key).append("=\"").append(value).append('"'));

					if (isEmptyTag(name)) {
						sb.append('/');
					}
					sb.append('>');
					break;
				}
				case XMLStreamConstants.END_ELEMENT:
					String name = event.asEndElement().getName().getLocalPart();
					if (!isEmptyTag(name)) {
						sb.append("</").append(name).append('>');
					}
					break;
				case XMLStreamConstants.CHARACTERS:
					Characters characters = event.asCharacters();
					if (!characters.isIgnorableWhiteSpace()) {
						characters.writeAsEncodedUnicode(new WriterAdapter(sb));
					}
					break;
			}
		}

		private boolean isEmptyTag(String name) {
			return "img".equalsIgnoreCase(name) || "hr".equalsIgnoreCase(name) || "br".equalsIgnoreCase(name);
		}
	}

	@RequiredArgsConstructor
	private final static class WriterAdapter extends Writer {

		private final StringBuilder sb;

		@Override
		public void write(char[] cbuf, int off, int len) {
			sb.append(cbuf, off, len);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}

	}

}
