package net.pawet.pawgen.component.xml;

import lombok.Cleanup;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.ArticleHeader;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.resource.ResourceFactory;
import net.pawet.pawgen.component.system.storage.Storage;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.Writer;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static net.pawet.pawgen.component.xml.XmlUtils.createXMLEventReader;
import static net.pawet.pawgen.component.xml.XmlUtils.getWithPrefix;

@Slf4j
public record ContentParser(Storage storage,
							ResourceFactory resourceFactory) {

	public static final Set<String> ROOT_TAG_NAMES = Set.of("article", "gallery");

	public CharSequence read(ArticleHeader header) {
		resourceFactory.createAttachmentResource(header.getFile());
		return read(header.getCategory(), header.getTitle());
	}

	@SneakyThrows
	CharSequence read(Category category, String title) {
		try (var is = storage.read(category.resolve("index.xml"))) {
			@Cleanup var xmlr = createXMLEventReader(is);
			return new ArticleContent(category, title).read(xmlr);
		}
	}

	@RequiredArgsConstructor
	private final class ArticleContent {

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
				case XMLStreamConstants.START_ELEMENT -> {
					StartElement startElement = event.asStartElement();
					QName qName = startElement.getName();
					String name = qName.getLocalPart();
					sb.append('<').append(name);

					var attributes = filter.filterAttributes(startElement.getAttributes())
						.filter(attr -> attr.getValue() != null)
						.filter(attr -> !attr.getValue().isEmpty())
						.collect(toMap(
							attr -> attr.getName().getLocalPart(), Attribute::getValue,
							(s1, s2) -> Optional.of(s1).filter(not(String::isBlank)).orElse(s2)
						));

					resourceFactory.createResource(name, category, attributes)
						.forEach((key, value) -> sb.append(' ').append(key).append("=\"").append(value).append('"'));

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
						characters.writeAsEncodedUnicode(new Writer() {
							@Override
							public void write(char @NonNull [] cbuf, int off, int len) {
								sb.append(cbuf, off, len);
							}

							@Override
							public void flush() {
							}

							@Override
							public void close() {
							}
						});
					}
				}
			}
		}

	}

	private static boolean isEmptyTag(String name) {
		return "img".equalsIgnoreCase(name) || "hr".equalsIgnoreCase(name) || "br".equalsIgnoreCase(name);
	}

}
