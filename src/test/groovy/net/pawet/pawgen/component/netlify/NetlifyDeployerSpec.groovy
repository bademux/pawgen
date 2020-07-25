package net.pawet.pawgen.component.netlify

import spock.lang.Specification
import java.lang.Void as Should

import java.util.stream.Stream

class NetlifyDeployerSpec extends Specification {

	Should "create and upload for #state"() {
		given:
		def data = [new TestFile('/test')]
		def client = Mock(NetlifyDeployer)
		def deployer = new Deployer(client, data)
		when:
		def isSuccess = deployer.deployWithState(state)
		then:
		isSuccess == false
		and:
		1 * client.createDeploy(data) >> Optional.of('deployId')
		0 * client.uploadFiles('deployId', data) >> false
		and: 'no more invocations'
		0 * _
		where:
		state << ['error', '--init--']
	}

	Should "upload files for #state"() {
		given:
		def data = [new TestFile('/test')]
		def client = Mock(NetlifyDeployer)
		def deployer = new Deployer(client, data, 'deployId')
		when:
		def isSuccess = deployer.deployWithState(state)
		then:
		isSuccess == true
		and:
		1 * client.getRequiredFilesFor('deployId') >> Stream.of('/test'.digest('SHA-1'))
		1 * client.uploadFiles('deployId', data) >> 1
		and: 'no more invocations'
		0 * _
		where:
		state << ['new', 'uploading', 'prepared']
	}

	Should "upload files for #state with no deployId"() {
		given:
		def client = Mock(NetlifyDeployer)
		def deployer = new Deployer(client, [])
		when:
		def isSuccess = deployer.deployWithState(state)
		then:
		isSuccess == true
		and: 'no more invocations'
		0 * _
		where:
		state << ['new', 'uploading']
	}

	Should "handle preparing state"() {
		given:
		def data = [new TestFile("/test_file")]
		def client = Mock(NetlifyDeployer)
		def deployer = new Deployer(client, data)
		when:
		def isSuccess = deployer.deployWithState('preparing')
		then:
		isSuccess == false
		and: 'no more invocations'
		0 * _
	}

	Should "test ready"() {
		given:
		def data = [new TestFile("/test_file")]
		def client = Mock(NetlifyDeployer)
		def deployer = new Deployer(client, data)
		when:
		def isSuccess = deployer.deployWithState('ready')
		then:
		isSuccess == true
		and: 'no more invocations'
		0 * _
	}

}
