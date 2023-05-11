package net.pawet.pawgen.component.markdown

import net.pawet.pawgen.component.Category
import spock.lang.Specification

import java.lang.Void as Should
import java.nio.channels.Channels

class ArticleParserSpec extends Specification {

	Should 'read content'() {
		given:
		var parser = new ArticleParser({ c, a -> a }, { c, a -> a })
		var data = parser.parseToDocument(Channels.newChannel(new ByteArrayInputStream('''
---
test: value1
---
![alt text](Isolated.png "Title")
[Link text Here](https://link-url-here.org)
'''.bytes)), Category.ROOT, null)
		when:
		var content = parser.readContent(data)
		then:
		content.toString() == '''\
<p><img src="Isolated.png" alt="alt text" title="Title" />
<a href="https://link-url-here.org">Link text Here</a></p>
'''
	}

}
