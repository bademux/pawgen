package net.pawet.pawgen.component.deployer

import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions
import util.RecordingWireMock

import java.lang.Void as Should

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import static java.lang.System.getenv

class NetlifyClientSpec extends Specification {

	Should 'create deploy'() {
		given:
		def siteId = getenv().getOrDefault('NETLIFY_SITEAPPID', 'testSiteId')
		def files = [new TestFile('test_file13.html'), new TestFile('test_file23.html')]
		when: 'create deploy'
		String deployId = client.siteDeploy(siteId).createAsync('pawgen_test_deploy', files).orElseThrow().id.string
		then:
		condition.eventually {
			assert client.deploy(deployId).find().get().required.string == files.digest
		}
		when: 'deploy 1st file'
		client.deploy(deployId).upload(files[0])
		then:
		condition.eventually {
			assert client.deploy(deployId).find().get().state.string == 'uploading'
		}
		when: 'deploy 2ns file'
		client.deploy(deployId).upload(files[1])
		then:
		condition.eventually {
			assert client.deploy(deployId).find().get().state.string == 'ready'
		}
	}

	@AutoCleanup
	def wireMock = new RecordingWireMock(
		options().withRootDirectory("src/test/resources/${this.class.simpleName}").port(8080).notifier(new Slf4jNotifier(true)),
		NetlifyClient.NETLIFY_BASE_URL
	).start()

	def client = new NetlifyClient("http://localhost:$wireMock.port".toURI(), getenv().getOrDefault('NETLIFY_ACCESSTOKEN', 'testToken'))

	def condition = new PollingConditions(delay: 30, initialDelay: 0.2)
}

