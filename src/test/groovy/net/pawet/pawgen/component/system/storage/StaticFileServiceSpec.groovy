package net.pawet.pawgen.component.system.storage

import com.google.common.jimfs.Jimfs
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.lang.Void as Should
import java.nio.file.FileSystem
import java.nio.file.Files

class StaticFileServiceSpec extends Specification {

	Should 'read article from fs'() {
		given:
		var outputDir = fs.getPath('/')
		var testFile = outputDir.resolve('text.txt')
		Files.writeString(testFile, 'test')
		var fsRegistry = new FileSystemRegistry()
		var service = new StaticFileService([testFile.toUri()], outputDir, fsRegistry, { true })
		when:
		var files = service.getStaticFiles()
		then:
		files.collectEntries({ [(it.key as String): it.value as String] }) == ['/text.txt': '/text.txt']
	}

	@AutoCleanup
	FileSystem fs = Jimfs.newFileSystem()

}
