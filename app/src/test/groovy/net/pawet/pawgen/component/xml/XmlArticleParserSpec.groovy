package net.pawet.pawgen.component.xml

import spock.lang.Specification

import java.lang.Void as Should
import java.time.ZonedDateTime

class XmlArticleParserSpec extends Specification {

	Should 'parse date "#value"'() {
		when:
		var date = XmlArticleParser.parseDate(value)
		then:
		date == ZonedDateTime.parse('2022-06-22T00:00Z')
		where:
		value << ['2022-06-22', '22-06-2022', '2022.06.22', '22.06.2022']
	}

}
