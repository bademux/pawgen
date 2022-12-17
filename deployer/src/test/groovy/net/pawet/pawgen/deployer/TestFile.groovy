package net.pawet.pawgen.deployer

import lombok.EqualsAndHashCode
import lombok.Getter
import net.pawet.pawgen.deployer.deployitem.Content
import net.pawet.pawgen.deployer.deployitem.Digest
import net.pawet.pawgen.deployer.deployitem.Path

@EqualsAndHashCode
final class TestFile implements Digest, Content, Path {

	@Getter
	final String digest
	@Getter
	final String path
	final InputStream inputStream

	TestFile(String relativePath) {
		this.path = relativePath
		def data = relativePath.bytes
		inputStream = new ByteArrayInputStream(data)
		digest = relativePath.digest('SHA-1')
	}

	@Override
	InputStream inputStream() {
		return inputStream
	}

}
