import groovy.io.FileType
import groovy.util.logging.Slf4j

import static spark.Spark.*
import spark.*
import groovy.json.JsonOutput

@Slf4j
class ResourceLoader {
    def gcl = new GroovyClassLoader()

    def engine = new groovy.text.SimpleTemplateEngine()

    def standardTemplates

    ResourceLoader() {
        loadStandardTemplates()
    }

    def loadStandardTemplate(action) {
        def baseText = new File("templates/${action}Spec.template.json").text
        engine.createTemplate(baseText)
    }

    def loadStandardTemplates() {
        standardTemplates = [
                'api'    : loadStandardTemplate('api'),
                'get'    : loadStandardTemplate('get'),
                'getById': loadStandardTemplate('getById'),
                'post'   : loadStandardTemplate('post'),
                'put'    : loadStandardTemplate('put'),
                'delete' : loadStandardTemplate('delete')
        ]
    }

    def loadClass(file) {
        gcl.parseClass(file).newInstance()
    }

    def loadSpec(resName) {
        def f = new File("resources/${resName}Spec.groovy")
        if (f.exists()) {
            return loadClass(f)
        }
    }

    def containsMethod(obj, methodName) {
        obj.metaClass.respondsTo obj, methodName
    }

    def getPlural(resource) {
        def m = resource =~ /(.*)(Resource.*)/
        def name = m[0][1]
        name = name[0].toLowerCase() + name.substring(1)
        name.plural()
    }

    def getCollectionName(resource) {
        if (containsMethod(resource, 'plural')) {
            resource.plural()
        }
        else {
            getPlural resource
        }
    }

    def loadResource(resFile, map) {
        def resName = (resFile.name =~ /(.*)Resource.groovy/)[0][1]
        def h = loadClass(resFile)
        def s = loadSpec(resName)
        map."$resName" = [
            name: resName,
            handler: h,
            plural: getCollectionName(h),
            specs: s,
            actions: [:]
        ]
    }

    def loadFiles(dirPath) {
        def map = [:]
        new File(dirPath).eachFileRecurse (FileType.FILES) {
            if ( it.name.endsWith('Resource.groovy')) {
                // to-do: remove this side effect later on
                loadResource(it, map)
            }
        }
        map
    }

    def registerResourceAction(resourceDesc, action) {
        resourceDesc.actions."$action" = action
        def col = resourceDesc.plural
        def restAction = action
        def path = "/$col"
        if (action == 'getById') {
            restAction = "get"
            path += '/:id'
        }

        spark.Spark."$restAction"(path, { req, res -> resourceDesc.handler."$action"(req, res) } )

        log.info "Registering $col.$action"
    }

    def registerActions(resourceDesc) {
        ['get', 'post', 'put', 'delete', 'getById'].each { action ->
            if (containsMethod(resourceDesc.handler, action)) {
                registerResourceAction resourceDesc, action
            }
        }
    }

    def scaffoldTemplate(template, binding) {
        def tpl = standardTemplates[template].make(binding)
        def finalText = tpl.toString()
        new groovy.json.JsonSlurper().parseText(finalText)
    }

    def registerSpecs(resourceDesc, doc) {
        if (resourceDesc.specs) {
            doc.definitions["/${resourceDesc.plural}"] = resourceDesc.specs.schema()
        }
        else {
            log.warn "No specs for resource: ${resourceDesc.name}."
        }

        if (!resourceDesc.actions.any()) {
            return
        }
        doc.paths["/${resourceDesc.plural}"] = [:]
        resourceDesc.actions.each {
            def s = scaffoldTemplate(it.key, ['plural': resourceDesc.plural, 'resource': resourceDesc.name, 'ref': '$ref' ])
            doc.paths["/${resourceDesc.plural}"]["${it.key}"] = s
        }
    }

    def registerResources(dirPath) {
        def map = loadFiles(dirPath)
        def doc = scaffoldTemplate('api', ['host': 'localhost', 'version': '0.1', 'description':'api description', 'title':'api title'])

        map.each {
            registerActions it.value
            registerSpecs it.value, doc
        }

        spark.Spark.get "/swagger", { req, res ->
            res.header("Access-Control-Allow-Origin", '*');
            res.header("Access-Control-Request-Method", '*');
            res.header("Access-Control-Allow-Headers", '*');

            JsonOutput.toJson(doc)
        }
    }

    def loadAllResources(){
        registerResources('resources')
    }
}