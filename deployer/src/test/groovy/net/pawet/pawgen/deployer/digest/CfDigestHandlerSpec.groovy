package net.pawet.pawgen.deployer.digest


import org.bouncycastle.jce.provider.BouncyCastleProvider
import spock.lang.Specification

import java.lang.Void as Should
import java.nio.ByteBuffer
import java.nio.file.Path
import java.security.Security
import java.util.concurrent.atomic.AtomicReference

class CfDigestHandlerSpec extends Specification {
	{
		Security.addProvider(new BouncyCastleProvider())
	}

	Should "calculate Cloudflare Pages digest"() {
		given:
		var data = new AtomicReference<byte[]>()
		def path = Path.of('robots.txt')
		var content = 'User-agent: serpstatbot\nDisallow: /\n'.bytes
		var handler = new CfDigestHandler(path, data::set)
		when:
		for (int i = 0; i < content.length; i += 2) {
			var cbb = Arrays.copyOfRange(content, i, Math.min(i + 2, content.length))
			handler.accept(ByteBuffer.wrap(cbb))
		}
		and:
		handler.close()
		then:
		verifyAll {
			(content.encodeBase64().toString() + 'txt').digest('BLAKE3-256').substring(0, 32) == '09fffe9da814514126d3050f42b123cf'
			data.get().encodeHex().toString() == '09fffe9da814514126d3050f42b123cf'
		}
	}

}
