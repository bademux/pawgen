package net.pawet.pawgen.component.xml

import spock.lang.Specification

import java.time.Instant
import java.lang.Void as Should

class ArticleParserSpec extends Specification {

	Should 'parse date'() {
		given:
		var dateStr = '29-06-2011'
		when:
		var date = ArticleParser.parseDate(dateStr)
		then:
		date == Instant.parse('2011-06-29T00:00:00Z')
	}

}
