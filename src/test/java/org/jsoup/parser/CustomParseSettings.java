//package org.jsoup.parser;
//
//import org.jsoup.nodes.Attributes;
//
//import java.util.Map;
//
//public class CustomParseSettings extends ParseSettings {
//	public CustomParseSettings() {
//		super(false, false);
//	}
//
//	@Override
//	Attributes normalizeAttributes(Attributes attributes) {
//		if (attributes == null) {
//			return null;
//		}
//		attributes = super.normalizeAttributes(attributes);
//		return attributes.asList().stream()
//			.sorted(Map.Entry.comparingByKey())
//			.collect(Attributes::new, Attributes::put, Attributes::addAll);
//	}
//
//}
