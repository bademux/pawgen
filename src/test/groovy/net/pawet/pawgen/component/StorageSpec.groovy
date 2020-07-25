package net.pawet.pawgen.component

import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

import static java.lang.Integer.MAX_VALUE
import static java.nio.file.Files.createDirectories
import static java.nio.file.Files.createFile
import static java.util.stream.Collectors.toList

class StorageSpec extends Specification {

	def "CopyStaticResources"() {
		given:
		def staticDir = createDirectories(fs.getPath('/staticDir1'))
		def staticDirTest2 = createDirectories(fs.getPath('/staticDir1/test1/test2'))
		createFile(staticDirTest2.resolve('test.txt'))
		def outDir = createDirectories(fs.getPath('/outDir'))
		def storage = Storage.of(null, outDir, staticDir, Instant.MIN)
		when:
		storage.copyStaticResources()
		then:
		read('/outDir') == ['/outDir/test1/test2/test.txt']
	}

	private List<String> read(String path) {
		try (def files = Files.walk(fs.getPath(path), MAX_VALUE)) {
			return files.filter(Files::isRegularFile).map(p -> p.toString()).collect(toList())
		}
	}

	@Shared
	def fs = FileSystems.newFileSystem(URI.create('memory:testfs:/'), [:])

	void cleanup() {
		fs.close()
	}
}
