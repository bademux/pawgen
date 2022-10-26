package net.pawet.pawgen

import spock.lang.Specification
import spock.lang.Unroll
import util.ImageUtil
import util.PawgenFs

import java.lang.Void as Should
import java.nio.file.Path

import static java.nio.file.Files.*
import static java.time.Instant.EPOCH
import static util.ImageUtil.createTestImageAsByte

class ApplicationSpec extends Specification {

	@Unroll
	Should 'build site #fileSystemProvider.method'() {
		given: 'filesystem'
		PawgenFs pawFs = fileSystemProvider.call()
		Path outputDir = pawFs.dir('out')
		Path templateDir = pawFs.dir('templates')
		Path contentDir = pawFs.dir('contentDir/data')
		Path filesDir = pawFs.dir('contentDir/files')
		Path staticDir = pawFs.dir('contentDir/static')
		and: 'pawgen config'
		def configFile = pawFs.writeProperties('config.properties', [
			contentDir      : contentDir.toUri() as String,
			staticDirs       : "${filesDir.toUri()},${staticDir.toUri()}**" as String,
			templatesDir    : templateDir.toUri() as String,
			outputDir       : outputDir.toUri() as String,
			hosts           : 'localhost',
			dateFrom        : EPOCH.toString(),
			'netlify.enable': false as String,
			'watermark.text': '', // disable as watermarking produces different result on win, it makes assertion not trivial
		]).toUri() as String
		and: 'site data'
		write(pawFs.dir("$contentDir/newcat/test/_img").resolve('toster.bmp'), createTestImageAsByte(251, 27))

		def newcatTestArticle = contentDir.resolve('newcat/test').resolve('index.xml')
		writeString(newcatTestArticle, '''\
<?xml version="1.0" encoding="UTF-8" ?>
<body xmlns:en="http://site/en" xmlns:by="http://site/by" xmlns:pl="http://site/pl">
<article en:title="illegal chars: *?|">
  <a href="http://example.com">external link</a>
  <a href="example">internal link</a>
  <a href="example/illegal chars: *?|">bad internal link</a>
  <a href="http://localhost">internal link with host</a>
  <img src="_img/toster.bmp"/>
</article>
</body>
''')
		def newcatArticle = contentDir.resolve('newcat').resolve('index.xml')
		writeString(newcatArticle, '''\
<?xml version="1.0" encoding="UTF-8" ?>
<body xmlns:en="http://site/en" xmlns:by="http://site/by" xmlns:pl="http://site/pl"><article en:title="newcat">newcat</article></body>
''')
		var imageBmp = ImageUtil.createTestImageAsByte(46, 27)
		write(contentDir.resolve('image.bmp'), imageBmp)
		writeString(contentDir.resolve('index.xml'), '''\
<?xml version="1.0" encoding="UTF-8" ?>
<body xmlns:en="http://site/en" xmlns:by="http://site/by" xmlns:pl="http://site/pl">
 <article en:title="Main"  by:title="Галоўная" file="staticFile.bin"><a href="/files/staticFile.bin">testArticle</a><img src="image.bmp"/></article>
</body>
''')
		writeString(staticDir.resolve('test.css'), 'test')
		write(filesDir.resolve('staticFile.bin'), 'test'.bytes)
		writeString(templateDir.resolve('index.html.mustache'), '''
Parent: {{parent.title}} "{{#func.relativize}}{{{parent.url}}}{{/func.relativize}}"
{{#children}}Child: {{title}} "{{#../func.relativize}}{{{url}}}{{/../func.relativize}}"\n{{/children}}
Content:
{{{.}}}
''')
		when:
		def result = Application.run([configFile])
		then:
		result == 0
		and:
		pawFs.listFiles(outputDir) == [
			'files/staticFile.bin',
			'Main.html',
			'test.css',
			'Галоўная.html',
			'newcat/newcat.html',
			'newcat/test/illegal_chars_____.html',
			'newcat/test/_img/toster.bmp',
		].collect(outputDir.&resolve) as Set
		and:
		verifyAll {
			readString(outputDir.resolve('Main.html')) == """
Parent:  ""
Child: newcat "newcat/newcat.html"
Content:
<a href="/files/staticFile.bin">testArticle</a><img src="data:image/bmp;base64,${imageBmp.encodeBase64()}" width="46" class="img_left" height="27"/>
"""
			readString(outputDir.resolve('Галоўная.html')) == """
Parent:  ""
Child: newcat "newcat/newcat.html"
Content:
<a href="/files/staticFile.bin">testArticle</a><img src="data:image/bmp;base64,${imageBmp.encodeBase64()}" width="46" class="img_left" height="27"/>
"""
			readString(outputDir.resolve('newcat/newcat.html')) == """
Parent: Main "../Main.html"
Child: illegal chars: *?| "test/illegal_chars_____.html"
Content:
newcat
"""
			readString(outputDir.resolve('newcat/test/illegal_chars_____.html')) == """
Parent: newcat "../newcat.html"
Content:

  <a href="http://example.com">external link</a>
  <a href="example">internal link</a>
  <a href="example/illegal chars: *?|">bad internal link</a>
  <a href="http://localhost">internal link with host</a>
  <img onClick="showLightbox(this, '_img/toster.bmp')" src="data:image/jpg;base64,/9j/4AAQSkZJRgABAgAAAQABAAD//gAIcGF3Z2Vu/9sAQwAKBwcIBwYKCAgICwoKCw4YEA4NDQ4dFRYRGCMfJSQiHyIhJis3LyYpNCkhIjBBMTQ5Oz4+PiUuRElDPEg3PT47/9sAQwEKCwsODQ4cEBAcOygiKDs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7/8AAEQgAGwD6AwEiAAIRAQMRAf/EAB8AAAEFAQEBAQEBAAAAAAAAAAABAgMEBQYHCAkKC//EALUQAAIBAwMCBAMFBQQEAAABfQECAwAEEQUSITFBBhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6g4SFhoeIiYqSk5SVlpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2drh4uPk5ebn6Onq8fLz9PX29/j5+v/EAB8BAAMBAQEBAQEBAQEAAAAAAAABAgMEBQYHCAkKC//EALURAAIBAgQEAwQHBQQEAAECdwABAgMRBAUhMQYSQVEHYXETIjKBCBRCkaGxwQkjM1LwFWJy0QoWJDThJfEXGBkaJicoKSo1Njc4OTpDREVGR0hJSlNUVVZXWFlaY2RlZmdoaWpzdHV2d3h5eoKDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uLj5OXm5+jp6vLz9PX29/j5+v/aAAwDAQACEQMRAD8A8ZooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKAP//Z" alt="_img/toster.bmp" width="250" title="_img/toster.bmp" class="g_img" height="27"/>

"""
		}
		and:
		pawFs.readAttributes(outputDir, 'sha1') == [
			(outputDir.resolve('newcat/test/_img/toster.bmp'))        : 'd11ee4e14abb11b2efc49e6a4e98350ec6d036be',
			(outputDir.resolve('newcat/test/illegal_chars_____.html')): '061a1ba2340e838bda8ebc1c13a1a0d3405cf505',
			(outputDir.resolve('newcat/newcat.html'))                 : '81af066defbe81eaae2a582479d0440699e10ecf',
			(outputDir.resolve('files/staticFile.bin'))               : 'a94a8fe5ccb19ba61c4c0873d391e987982fbbd3',
			(outputDir.resolve('test.css'))                           : 'a94a8fe5ccb19ba61c4c0873d391e987982fbbd3',
			(outputDir.resolve('Main.html'))                          : '050d8ffd6acd62b1efe50656a8099e440dddee24',
			(outputDir.resolve('Галоўная.html'))                      : '050d8ffd6acd62b1efe50656a8099e440dddee24',
		]
		cleanup:
		pawFs.close()
		where:
		fileSystemProvider << [
			PawgenFs::unixWithUserAttrs,
			PawgenFs::unix,
			PawgenFs::win,
			PawgenFs::osx,
			PawgenFs::tmpZipFs,
			PawgenFs::tmpFs,
		]
	}

}
