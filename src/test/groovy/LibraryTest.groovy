/*
 * This Spock specification was auto generated by running 'gradle init --type groovy-library'
 * by 'diogo' at '4/24/15 12:50 PM' with Gradle 2.3
 *
 * @author diogo, @date 4/24/15 12:50 PM
 */

import spock.lang.Specification

class LibraryTest extends Specification{
    def "someLibraryMethod returns true"() {
        setup:
        Library lib = new Library()
        when:
        def result = lib.someLibraryMethod()
        then:
        result == true
    }
}
