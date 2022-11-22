package net.pawet.pawgen.component.resource


import spock.lang.Specification

import java.lang.Void as Should

class ResourceProcessorSpec extends Specification {

	Should "create handle link"() {
		given:
		def factory = new ResourceProcessor(null, null, ['localhost'] as Set)
		when:
		def result = factory.handleLink(url)
		then:
		result == expectedUrl
		where:
		url                     || expectedUrl
		'http://example.com'    || null
		'/test'                 || '/test'
		'http://localhost/tost' || '/tost'
		' #test'                || null
	}

}
