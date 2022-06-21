package net.pawet.pawgen.component.render


import spock.lang.Specification
import spock.lang.Unroll

import java.lang.Void as Should

class DateFormatterSpec extends Specification {

	@Unroll
	Should 'parse date "#value"'() {
		when:
		var formatted = TemplateFunctions.format(value)
		then:
		formatted == expected
		where:
		value                                         || expected
		'2022-06-22T17:29:58.306027100Z'              || '2022-06-22T17:29:58.3060271Z'
		'2022-06-22T17:29:58.3060271Z'                || '2022-06-22T17:29:58.3060271Z'
		'2022-06-22T17:29:58.306Z'                    || '2022-06-22T17:29:58.306Z'
		'2022-06-22T17:29'                            || '2022-06-22T17:29:00'
		'2022-06-22T17:29-08:00'                      || '2022-06-22T17:29:00-08:00'
		'2022-06-22 17:29'                            || '2022-06-22T17:29:00'
		' 2022-06-22T17:29:58Z | dd-MM-yyyy '         || '22-06-2022'
		'2022-06-22T17:29:58.306027100Z | dd-MM-yyyy' || '22-06-2022'
		' 2022-06-22T17:29:58Z '                      || '2022-06-22T17:29:58Z'
		' 2022-06-22T17:29:58'                        || '2022-06-22T17:29:58'
		''                                            || ''
	}
}
