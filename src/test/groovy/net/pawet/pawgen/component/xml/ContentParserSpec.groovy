package net.pawet.pawgen.component.xml

import spock.lang.Specification

import java.lang.Void as Should

class ContentParserSpec extends Specification {

	Should 'parse content'() {
		given:
		var contentParser = new ContentParser({ __, args -> args })
		var data = new ByteArrayInputStream('''\
<?xml version="1.0" encoding="UTF-8" ?>
<body xmlns:en="http://site/en" xmlns:by="http://site/by" xmlns:pl="http://site/pl"><article en:title="category2">test<p>test<i>link</i></p><i>link</i></article></body>
'''.bytes)
		when:
		var content = contentParser.read(data, 'category2') as String
		then:
		content == 'test<p>test<i>link</i></p><i>link</i>'
	}

}
