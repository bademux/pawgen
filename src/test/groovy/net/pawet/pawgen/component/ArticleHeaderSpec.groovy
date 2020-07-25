package net.pawet.pawgen.component

import spock.lang.Specification
import spock.lang.Unroll

class ArticleHeaderSpec extends Specification {
	@Unroll
	def "test normalizeTitle with #title"() {
		when:
		def result = ArticleHeader.normalizeTitle(title, ".html")
		then:
		result == expected
		where:
		title                                                                                                                                    || expected
//		''                                                                                                                                       || '.html'
//		'" "'                                                                                                                                    || "'_'.html"
//		'a'.repeat(255)                                                                                                                          || 'a'.repeat(250) + '.html'
//		'b'.repeat(256)                                                                                                                          || 'b'.repeat(250) + '.html'
		'Картографирование некоторых популярных русских астронимов на основе астронимической картотеки УРФУ с привлечением словарных материалов' || 'Картографирование_некоторых_популярных_русских_астронимов_на_основе_астронимической_картотеки_УРФУ_с_привлечением_словарных_материалов.html'
	}

}
