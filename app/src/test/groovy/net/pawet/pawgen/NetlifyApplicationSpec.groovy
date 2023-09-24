package net.pawet.pawgen

import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.Options
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import net.pawet.pawgen.deployer.NetlifyClient
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions
import util.PawgenFs
import util.RecordingWireMock

import java.lang.Void as Should

import static java.lang.System.getenv
import static java.nio.file.Files.write
import static java.nio.file.Files.writeString
import static java.util.stream.Collectors.toMap
import static util.ImageUtil.createTestImageAsByte

class NetlifyApplicationSpec extends Specification {

	@Unroll
	Should 'deploy site #fileSystemProvider.method'() {
		given: 'filesystem'
		PawgenFs pawFs = fileSystemProvider.call()
		def outputDir = pawFs.dir('out')
		def templateDir = pawFs.dir('templates')
		def contentDir = pawFs.dir('contentDir/data')
		def filesDir = pawFs.dir('contentDir/files')
		def configFile = pawFs.writeProperties('config.properties', [
			contentDir           : contentDir.toUri() as String,
			staticDirs           : filesDir.toUri() as String,
			templatesDir         : templateDir.toUri() as String,
			outputDir            : outputDir.toUri() as String,
			deployers            : 'NETLIFY',
			'netlify.url'        : netlifyUrl as String,
			'netlify.accessToken': netlifyAccessToken,
			'netlify.siteAppId'  : netlifySiteAppId,
		]).toUri() as String
		and: 'site data'
		write(contentDir.resolve('image.bmp'), createTestImageAsByte(46, 27))
		writeString(contentDir.resolve('index.by.md'), '''\
---
title: Галоўная
type: article
file: staticFile.bin
---
[testArticle](/files/staticFile.bin) ![alt text](image.bmp)
''')
		write(filesDir.resolve('staticFile.bin'), 'test'.bytes)
		writeString(templateDir.resolve('index.html.mustache'), '{{{.}}}')
		and:
		def siteDeploy = client.siteDeploy(netlifySiteAppId)
		when:
		def result = Application.run([configFile])
		then:
		result == 0
		and:
		verifyAll(netlifyServer.wireMock.serveEvents.requests.request.url) {
			it.findAll({ it ==~ /\/deploys\/(.*)/ }).size() == 1
			it.findAll({ it ==~ /\/sites\/(.*)\/deploys\?title=pawgen_deployer/ }).size() == 1
			it.findAll({ it ==~ /\/sites\/(.*)\/deploys\?per_page=1&state=prepared/ }).size() == 1
			it.size() == 3
		}
		and: 'netlify deployed'
		condition.eventually {
			siteDeploy.find().orElseThrow().published_deploy.state.string == 'ready'
		}
		and:
		condition.eventually {
			siteDeploy.files().collect(toMap({ it.path.string }, { it.sha.string })) == [
				'/files/staticfile.bin': 'a94a8fe5ccb19ba61c4c0873d391e987982fbbd3',
				'/image.bmp'           : '75443c0f6787be87868588f1b1e22955a64d5124',
				'/галоўная.html'       : '94de541ed98bf4a7d97b666eebac3c4eb16baf52',
			] as HashMap
		}
		cleanup:
		pawFs.close()
		where:
		fileSystemProvider << [
			PawgenFs::unix,
			PawgenFs::win,
			PawgenFs::osx,
		]
	}

	@AutoCleanup
	def netlifyServer = new RecordingWireMock(
		WireMockConfiguration.options()
			.extensions(new ResponseTemplateTransformer(false, "string-length", { String context, Options options ->
				context.length()
			}))
			.withRootDirectory("src/test/resources/$NetlifyApplicationSpec.simpleName")
			.dynamicPort()
			.notifier(new Slf4jNotifier(true)),
		NetlifyClient.NETLIFY_BASE_URL
	).start()

	URI netlifyUrl = "http://localhost:$netlifyServer.port/".toURI()
	String netlifyAccessToken = getenv().getOrDefault('NETLIFY_ACCESSTOKEN', 'testToken')
	String netlifySiteAppId = getenv().getOrDefault('NETLIFY_SITEID', 'ffb9e628-4835-4b68-ab2a-5bfdd0b42348')

	def client = new NetlifyClient(netlifyUrl, netlifyAccessToken)

	def condition = new PollingConditions(delay: 30, initialDelay: 0.2)
}
