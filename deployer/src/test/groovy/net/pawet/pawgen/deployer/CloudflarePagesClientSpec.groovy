package net.pawet.pawgen.deployer

import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import spock.lang.AutoCleanup
import spock.lang.Specification
import util.RecordingWireMock

import java.lang.Void as Should

import static java.lang.System.getenv

class CloudflarePagesClientSpec extends Specification {

	Should "upload site"() {
		given:
		var project = client.project(accountId, projectName)
		and:
		var asset = project.asset()
		and:
		var deployment = project.deployment()
		and:
		def files = [new TestFile('file3.txt'), new TestFile('image3.bmp')]
		when:
		var missing = asset.missing(files)
		then:
		missing != [
			'ce1be0ff4065a6e9415095c95f25f47a633cef2b',
			'37de336e32a83986cc608ae291d10f427149cf4b'
		]
		when:
		var uploadRes = asset.upload(files)
		then:
		uploadRes != false
		when:
		var id = deployment.create(files, null)
		then:
		deployment.list().contains(id) == true
		and:
		asset.upsert(files) != false
	}

	@AutoCleanup
	RecordingWireMock server = new RecordingWireMock(
		WireMockConfiguration.options()
			.withRootDirectory("src/test/resources/$CloudflarePagesClientSpec.simpleName")
			.dynamicPort()
			.notifier(new Slf4jNotifier(true)),
            CloudflarePagesClient.BASE_URL
	).start()

	URI baseUrl = "http://localhost:$server.port".toURI()
	String accountId = getenv().getOrDefault('CLOUDFLARE_ACCOUNTID', '8dff37f48370a145196aca7561d61f53')
	String projectName = getenv().getOrDefault('CLOUDFLARE_PROJECTNAME', 'pawet')
	String token = getenv().getOrDefault('CLOUDFLARE_TOKEN', 'token')

	CloudflarePagesClient client = new CloudflarePagesClient(baseUrl, token)


}

