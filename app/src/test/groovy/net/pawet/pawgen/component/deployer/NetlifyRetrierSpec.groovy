package net.pawet.pawgen.component.deployer

import spock.lang.Specification

import java.time.Duration
import java.util.function.BooleanSupplier

class NetlifyRetrierSpec extends Specification {

	def "deploy With Retry"() {
		given:
		def deployer = Mock(BooleanSupplier)
		def retrier = new Retrier(null, Duration.ofMillis(0), 1)
		when:
		retrier.deployWithRetry(deployer)
		then:
		1 * deployer.getAsBoolean() >> true
	}

	def "deployWithRetry with max operation"() {
		given:
		def deployer = Mock(BooleanSupplier)
		def retrier = new Retrier(null, Duration.ofMillis(0), 1)
		when:
		retrier.deployWithRetry(deployer)
		then:
		def e = thrown(IllegalStateException)
		e.message == '2 operations in a row was done, limit reached.'
		2 * deployer.getAsBoolean() >> false
	}

}
