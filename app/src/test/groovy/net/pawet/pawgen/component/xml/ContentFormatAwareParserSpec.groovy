package net.pawet.pawgen.component.xml

import spock.lang.Specification

import java.lang.Void as Should
import java.nio.channels.Channels

class ContentFormatAwareParserSpec extends Specification {

	Should 'parse content'() {
		given:
		var contentParser = new ContentParser({ __, args -> args })
		var data = Channels.newChannel(new ByteArrayInputStream('''\
<?xml version="1.0" encoding="UTF-8" ?>
<body title="category2" type="article">test<p>test<i>link</i></p><i>link</i></body>
'''.bytes))
		when:
		var content = contentParser.read(data) as String
		then:
		content == 'test<p>test<i>link</i></p><i>link</i>'
	}

}
