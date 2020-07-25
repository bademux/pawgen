package net.pawet.pawgen.component

import spock.lang.Specification
import spock.lang.Unroll

class CategorySpec extends Specification {

    @Unroll
    def 'test relativize url "#category" "#url"'() {
        given:
        def cat = Category.of(category as String[])
        when:
        def res = cat.relativize(url).toString()
        then:
        res == expected
        where:
        category              | url              || expected
        []                    | ''               || ''
        ['test']              | 'tost'           || 'test/tost'
        ['test1', 'tost1']    | '/test2/tost2'   || '../../test2/tost2'
        ['test', 'tost', 'x'] | '/test/tost'     || '../'
        ['test', 'tost']      | '/test/tost/y'   || 'y'
        ['test', 'tost', 'y'] | '/test/tost/x/z' || '../x/z'
    }
}
