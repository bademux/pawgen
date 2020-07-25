package net.pawet.pawgen.component.xml;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.xml.namespace.QName;
import javax.xml.stream.EventFilter;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;
import java.util.Iterator;
import java.util.stream.Stream;

import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;

@RequiredArgsConstructor
class PawFilter implements EventFilter {

	@NonNull
	private final String prefix;
	private QName nonValidTagName;

	@Override
	public boolean accept(XMLEvent event) {
		if (nonValidTagName != null) {
			if (event.isEndElement() && event.asEndElement().getName().equals(nonValidTagName)) { //ignore endTag of invalidLang
				nonValidTagName = null;
				return false;
			}
			return false;
		}
		switch (event.getEventType()) {
			case XMLStreamConstants.START_DOCUMENT:
			case XMLStreamConstants.END_DOCUMENT:
				return false;
			case XMLStreamConstants.START_ELEMENT:
				QName qName = event.asStartElement().getName();
				if (!isValidPrefix(qName.getPrefix())) {
					nonValidTagName = qName;
					return false;
				}
				return true;
			case XMLStreamConstants.ATTRIBUTE:
				return !isValidPrefix(event.asStartElement().getName().getPrefix());
			case XMLStreamConstants.END_ELEMENT:
			default:
				return true;
		}
	}

	private boolean isValidPrefix(String prefix) {
		return prefix.isEmpty() || this.prefix.equalsIgnoreCase(prefix);
	}

	public Stream<Attribute> filterAttributes(Iterator<Attribute> itr) {
		return stream(spliteratorUnknownSize(itr, 0), false)
			.filter(attr -> isValidPrefix(attr.getName().getPrefix()));
	}
}
