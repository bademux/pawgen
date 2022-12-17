package net.pawet.pawgen.deployer

import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class NetlifyRetrierSpec extends Specification {

	def "deploy With Retry"() {
		given:
		var counter = new AtomicInteger(0)
		def retrier = new Retrier(null, Duration.ofMillis(0), 1)
		when:
		retrier.exec(counter::incrementAndGet)
		then:
		counter.get() == 1
	}

	def "deployWithRetry with max operation"() {
		given:
		var counter = new AtomicInteger(0)
		def deployer = { counter.incrementAndGet(); throw DeployerHttpException.generic(-1, 'test')}
		def retrier = new Retrier(null, Duration.ofMillis(0), 2)
		when:
		retrier.exec(deployer)
		then:
		def e = thrown(IllegalStateException)
		e.message == '2 operations in a row was done, limit reached.'
		counter.get() == 2
	}

}
