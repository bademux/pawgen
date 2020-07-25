package net.pawet.pawgen.component.netlify

import lombok.EqualsAndHashCode
import lombok.Getter

@EqualsAndHashCode
final class TestFile implements FileDigest, FileData {

	@Getter
	final String digest
	@Getter
	final String rootRelativePath
	final InputStream inputStream

	TestFile(String relativePath) {
		this.rootRelativePath = relativePath
		def data = relativePath.bytes
		inputStream = new ByteArrayInputStream(data)
		digest = relativePath.digest('SHA-1')
	}

	@Override
	InputStream inputStream() {
		return inputStream
	}

}
