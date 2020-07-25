//package net.pawet.pawgen.component.util
//
//import net.pawet.pawgen.component.xml.ContentParser
//import spock.lang.Specification
//
//import javax.xml.stream.XMLInputFactory
//import java.nio.file.Path
//
//class ContentParserSpec extends Specification {
//
//	def "Should pass simple filter"() {
//		given:
//		def xml = '''<?xml version="1.0" encoding="UTF-8"?>
//<body xmlns:en="en" xmlns:by="by">
//<article>
//\tallowedContent
//\t<en:testTag>testEnTagTestContent</en:testTag>
//\t<by:testTag>testByTagTestContent</by:testTag>
//</article>
//</body>'''
//		def xmlFactory = XMLInputFactory.newInstance()
//		def eventReader = xmlFactory.createXMLEventReader(new StringReader(xml))
//		def reader = xmlFactory.createFilteredReader(eventReader, new ContentParser.PawFilter('by'))
//		when:
//		def result = reader.iterator().collect().join()
//		then:
//		result == '''<body xmlns:en='en' xmlns:by='by'>
//<article>
//\tallowedContent
//\t
//\t<['by']:by:testTag>testByTagTestContent</['by']:by:testTag>
//</article>
//</body>'''
//	}
//
//	def "Should pass simple filter for attributes"() {
//		given:
//		def xml = '''<?xml version="1.0" encoding="UTF-8"?><body xmlns:en="en" xmlns:by="by"><article  en:attr="enTest" by:attr="byTest"/></body>'''
//		def xmlFactory = XMLInputFactory.newInstance()
//		def eventReader = xmlFactory.createXMLEventReader(new StringReader(xml))
//		def filter = new ContentParser.PawFilter('by')
//		def startElement = xmlFactory.createFilteredReader(eventReader, filter).iterator()
//			.find({ it.isStartElement() && it.name.localPart == 'article' })
//			.attributes.collect()
//		when:
//		def result = filter.filterAttributes(startElement).iterator().join()
//		then:
//		result == "by:attr='byTest'"
//	}
//
//	def "Should skip tag with wrong lang prefix"() {
//		given:
//		def reader = new ContentParser(Path.of("./"))
//		def xml = """<?xml version="1.0" encoding="UTF-8" ?>
//<body xmlns:en="en" xmlns:by="by">
//\t<article en:title="testEn" by:title="testBy">
//\t\t<hr/>
//\t\t<en:span>entext1<hr/>ntext2</en:span>
//\t\t<by:span>bytext1</by:span>
//\t</article>
//</body>"""
//		when:
//		def content = reader.read(new ByteArrayInputStream(xml.bytes), '', 'testBy')
//		then:
//		content.toString() == '<hr/><span>bytext1</span>'
//	}
//
//
//	def "Should parse text with witespace correctky"() {
//		given:
//		def reader = new ContentParser(Path.of("./"))
//		def xml = """<?xml version="1.0" encoding="UTF-8" ?>
//<body xmlns:ru="http://pawet.net/ru">
// <article ru:title="Лобач В.А. Эрос в белорусской традиционной культуре" source="Белорусский эротический фольклор. Москва, 2006" file="erot.djvu" date="07-06-2013">
//x
//y
//&lt;
// </article>
//</body>"""
//		when:
//		def content = reader.read(new ByteArrayInputStream(xml.bytes), 'library/v_ethnography/erot/03', 'Лобач В.А. Эрос в белорусской традиционной культуре')
//		then:
//		content.toString() == '''
//x
//y
//&lt;
//'''
//	}
//
//	def "Should read article with one tag"() {
//		given:
//		def reader = new ContentParser(Path.of("./"))
//		def xml = """<?xml version="1.0" encoding="UTF-8"?>
//<body xmlns:en="en" xmlns:by="by">
//<article en:title="testEn"  by:title="testBy" source="testSource">
//\ttestContentArticle
//\t<internalCommon>
//\ttestContentInternalCommon
//\t\t<en:testTag>
//\t\t\ttestTagTestContent
//\t\t\t\t<by:shouldBeIgnored>ShouldBeIgnoredText</by:shouldBeIgnored>
//\t\t\t\t<shouldBeHandled>ShouldBeHandledText</shouldBeHandled>
//\t\t</en:testTag>
//\t\t<by:testTag>
//\t\t\ttestTagTestContent
//\t\t\t<by:shouldNotBeHandled>ShouldNotBeHandledText</by:shouldNotBeHandled>
//\t\t\t<common>commonText</common>
//\t\t</by:testTag>
//\t</internalCommon>
//</article>
//</body>"""
//		when:
//		def content = reader.read(new ByteArrayInputStream(xml.bytes), 'testCategory', 'testEn')
//		then:
//		content.toString() == '''
//\ttestContentArticle
//\t<internalCommon>
//\ttestContentInternalCommon
//\t\t<testTag>
//\t\t\ttestTagTestContent
//\t\t\t\t<shouldBeHandled>ShouldBeHandledText</shouldBeHandled></testTag></internalCommon>'''
//	}
//
//	def "Should read article with two lang specific tags"() {
//		given:
//		def reader = new ContentParser(Path.of("./"))
//		def xml = """<?xml version="1.0" encoding="UTF-8"?>
//<body xmlns:en="en" xmlns:by="by">
//<en:article title="testEn" source="testSource">
//\ttestContentArticle
//\t<internalCommon en:title="testEn"  by:title="testBy">
//\ttestContentInternalCommon
//\t\t<by:testTag>
//\t\t\ttestTagTestContent
//\t\t\t<by:shouldBeHandled>ShouldBeHandledText</by:shouldBeHandled>
//\t\t\t<shouldBeIgnored>ShouldBeIgnoredText</shouldBeIgnored>
//\t\t</by:testTag>
//\t</internalCommon>
//</en:article>
//<by:article title="testBy" source="testSource">
//\ttestContentArticle
//\t<internalCommon en:title="testEn"  by:title="testBy">
//\ttestContentInternalCommon
//\t\t<testTag>
//\t\t\ttestTagTestContent
//\t\t\t<by:shouldBeHandled1>ShouldBeIgnoredText</by:shouldBeHandled1>
//\t\t\t<shouldBeHandled>ShouldBeHandledText</shouldBeHandled>
//\t\t</testTag>
//\t</internalCommon>
//</by:article>
//</body>"""
//		when:
//		def content = reader.read(new ByteArrayInputStream(xml.bytes), '', 'testBy')
//		then:
//		content.toString() == '''
//\ttestContentArticle
//\t<internalCommon title="testBy">
//\ttestContentInternalCommon
//\t\t<testTag>
//\t\t\ttestTagTestContent
//\t\t\t<shouldBeHandled1>ShouldBeIgnoredText</shouldBeHandled1><shouldBeHandled>ShouldBeHandledText</shouldBeHandled></testTag></internalCommon>'''
//	}
//
//}
