package net.pawet.pawgen.component.markdown

import net.pawet.pawgen.component.Category
import net.pawet.pawgen.component.system.storage.ArticleResource
import spock.lang.Specification

import java.lang.Void as Should
import java.nio.channels.Channels

class MDArticleParserSpec extends Specification {

	Should 'read content'() {
		given:
		var parser = MDArticleParser.of(MDArticleParserSpec::appendCategoryToLink, MDArticleParserSpec::appendCategoryToLink)
		var article = parser.parse(Channels.newChannel(new ByteArrayInputStream('''
---
type: article
title: title1
language: en
---
<span>test</span>
![alt text](Isolated.png "Title"){width=200}
[Link text Here](article.html)

<img src="htmltag.jpg" /><a href="link.html">test</a>
'''.bytes)), Category.of('test'), null)
			.resource(new ArticleResource(null, null, null))
			.build()
		when:
		var content = article.readContent()
		then:
		content.toString() == '''\
<p><span>test</span>
<img src="test/Isolated.png" alt="alt text" title="Title" width="200" />
<a href="test/article.html">Link text Here</a></p>
<p><img src="htmltag.jpg" /><a href="link.html">test</a></p>
'''
	}

	private static Map<String, String> appendCategoryToLink(category, Map<String, String> attrs) {
		var handle = (_, v) -> category.resolve(v)
		attrs.computeIfPresent('href', handle)
		attrs.computeIfPresent('src', handle)
		return attrs
	}

}
