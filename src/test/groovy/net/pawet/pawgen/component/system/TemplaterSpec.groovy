package net.pawet.pawgen.component.system

import net.pawet.pawgen.component.render.Templater
import spock.lang.Specification

import static java.net.URLEncoder.encode
import static java.nio.charset.StandardCharsets.UTF_8

class TemplaterSpec extends Specification {

	def 'Should render App properties'() {
		given:
		def render = new Templater({ new StringReader('{{App.title}}') })
		def writer = new StringWriter()
		def context = RenderContext.builder().templateName('')
			.context('App', ['title': 'testTitle'])
			.build()
		when:
		render.render(context, writer)
		then:
		writer.toString() == 'testTitle'
	}

	def 'Should render lt gt'() {
		given:
		def render = new Templater({ new StringReader('{{{content}}}') })
		def writer = new StringWriter()
		def context = RenderContext.builder().templateName('')
			.context('content', '''&lt;...&gt''')
			.build()
		when:
		render.render(context, writer)
		then:
		writer.toString() == '&lt;...&gt'
	}

	def 'Should List languages'() {
		given:
		def render = new Templater({
			new StringReader('''
{{#avalLangs}}<option {{#isCurrentLang}}selected="selected"{{/isCurrentLang}} value="{{lang}}">{{desc}}</option>{{/avalLangs}}
''')
		})
		def writer = new StringWriter()
		def context = RenderContext.builder().templateName('')
			.context('avalLangs', [
				['isCurrentLang': true, 'lang': 'by', 'desc': 'byLang'],
				['isCurrentLang': false, 'lang': 'en', 'desc': 'enLang']
			])
			.build()
		when:
		render.render(context, writer)
		then:
		writer.toString() == '\n<option selected="selected" value="by">byLang</option><option  value="en">enLang</option>\n'
	}

	def 'Should choose right category'() {
		given:
		def render = new Templater({ new StringReader('{{#App.category}}content{{/App.category}}{{^App.category}}contentRoot{{/App.category}}') })
		def writer = new StringWriter()
		def context = RenderContext.builder().templateName('')
			.context('App', ['category': category])
			.build()
		when:
		render.render(context, writer)
		then:
		writer.toString() == expectedCategoryClass
		where:
		category << [null, 'someCat']
		expectedCategoryClass << ['contentRoot', 'content']
	}

	def 'Should show current menu items'() {
		given:
		def render = new Templater({
			new StringReader('''
{{#menu.currentItems}}{{^App.isCurrentLang}}
\t<a href="{{menu.currentItemPath}}/{{titleUrlenc}}.html" title="{{title}}"><img src="/res/img/flag_{{lang}}.png" alt="{{title}}" border="1"/></a>
{{/App.isCurrentLang}}{{/menu.currentItems}}
''')
		})
		def writer = new StringWriter()
		def context = RenderContext.builder().templateName('')
			.context('App', [isCurrentLang: false])
			.context('menu',
				['currentItemPath': '/rootCurr',
				 currentItems     : [
					 [title: 'Test A', titleUrlenc: encode('Test A', UTF_8), lang: 'en']
				 ]
				])
			.build()
		when:
		render.render(context, writer)
		then:
		writer.toString() == '\n\t<a href="/rootCurr/Test+A.html" title="Test A"><img src="/res/img/flag_en.png" alt="Test A" border="1"/></a>\n'
	}

	def 'Should show menu items'() {
		given:
		def render = new Templater({
			new StringReader('''
{{#menu}}
\t{{^isCurrentArticle}}<p></p>{{/isCurrentArticle}}
\t{{#isCurrentArticle}}<li><a href="{{path}}{{articleTitleUrlenc}}.html">{{articleTitle}}</a></li>{{/isCurrentArticle}}
{{/menu}}
''')
		})
		def writer = new StringWriter()
		def context = RenderContext.builder().templateName('')
			.context('isCurrentArticle', isCurrentArticle)
			.context('menu', ['path': '/root/', articleTitle: 'Test A', articleTitleUrlenc: encode('Test A', UTF_8)])
			.build()
		when:
		render.render(context, writer)
		then:
		writer.toString() == expectedResult
		where:
		isCurrentArticle << [false, true]
		expectedResult << [
			'\n\t<p></p>\n\t\n',
			'\n\t\n\t<li><a href="/root/Test+A.html">Test A</a></li>\n'
		]
	}

}
