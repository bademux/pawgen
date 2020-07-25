//package net.pawet.pawgen.component.util
//
//import net.pawet.pawgen.component.xml.HeaderParser
//import org.junit.Rule
//import spock.lang.Specification
//
//import static util.FileSystemRule.createIndexFile
//
//class HeaderParserSpec extends Specification {
//
//	def "Should collect article data"() {
//		given:
//		def rootPath = fsRule.getRoot()
//		def creator = new HeaderParser(rootPath)
//		createIndexFile(rootPath).withWriter {
//			it.write """<?xml version="1.0" encoding="UTF-8"?>
//<body xmlns:en="en" xmlns:by="by" >
//<article en:title="testEn"  by:title="testBy" source="testSource" en:author="TestAuthorEn"></article>
//</body>"""
//		}
//		createIndexFile(rootPath.resolve("subcategory")).withWriter {
//			it.write """<?xml version="1.0" encoding="UTF-8"?>
//<body xmlns:en="en" xmlns:by="by" >
//<by:article title="testBy" source="testSource" file="TestFile1" date="TestDate1"></by:article>
//<en:article title="testEn" source="testSource" file="TestFile2" date="TestDate2"></en:article>
//</body>"""
//		}
//		when:
//		def headers = creator.parse()
//		then:
//		headers.iterator().collect().collect({ it.properties.findAll { it.key != 'class' }.sort() })
//			.sort({ it.lang }).sort({ it.title }).sort({ it.category }) == [
//			[
//				author          : 'TestAuthorEn',
//				category        : '',
//				date            : null,
//				file            : null,
//				lang            : 'en',
//				lastModifiedTime: 0,
//				source          : 'testSource',
//				title           : 'testEn',
//				type            : 'article',
//			], [
//				author          : null,
//				category        : '',
//				date            : null,
//				file            : null,
//				lang            : 'by',
//				lastModifiedTime: 0,
//				source          : 'testSource',
//				title           : 'testBy',
//				type            : 'article',
//			], [
//				author          : null,
//				category        : 'subcategory',
//				date            : 'TestDate2',
//				file            : 'TestFile2',
//				lang            : 'en',
//				lastModifiedTime: 0,
//				source          : 'testSource',
//				title           : 'testEn',
//				type            : 'article',
//			], [
//				author          : null,
//				category        : 'subcategory',
//				date            : 'TestDate1',
//				file            : 'TestFile1',
//				lang            : 'by',
//				lastModifiedTime: 0,
//				source          : 'testSource',
//				title           : 'testBy',
//				type            : 'article',
//			],
//		].sort({ it.lang }).sort({ it.title }).sort({ it.category })
//
//	}
//
//	@Rule
//	FileSystemRule fsRule = new FileSystemRule()
//
//}
