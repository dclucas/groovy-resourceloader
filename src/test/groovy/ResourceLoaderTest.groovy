import spock.lang.Specification
import groovyx.net.http.RESTClient
import static groovyx.net.http.ContentType.JSON

class ResourceLoaderTest extends Specification{
    def "someLibraryMethod returns true"() {
        setup:
        when:
        def client = new RESTClient("http://localhost:4567/", JSON)
        def resp = client.get(path : "swagger")
        println resp
        then:
        resp.status == 200
        resp.responseData
        resp.responseData.definitions
    }
}
