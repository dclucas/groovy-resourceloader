import groovy.io.FileType
import groovy.text.SimpleTemplateEngine
import groovy.util.logging.Slf4j
import groovy.json.JsonOutput

import static spark.Spark.*
import spark.*

@Slf4j
class ResourceLoader {
    private def gcl = new GroovyClassLoader()
    private def engine = new SimpleTemplateEngine()
    private def standardTemplates
    private def apiCfg
    private def cfg

    ResourceLoader(
        specProperties = ['host': 'localhost', 'version': '0.1', 'description': 'api description', 'title': 'api title'],
        cfg = [templatesDir: 'src/main/resources/templates', resourcesDir:'src/main/groovy/resources']) {
        this.apiCfg = specProperties
        this.cfg = cfg
        loadStandardTemplates()
    }

    private def loadStandardTemplate(action) {
        def baseText = new File("${cfg.templatesDir}/${action}Spec.template.json").text
        engine.createTemplate(baseText)
    }

    private def loadStandardTemplates() {
        standardTemplates = [
            'api'    : loadStandardTemplate('api'),
            'get'    : loadStandardTemplate('get'),
            'getById': loadStandardTemplate('getById'),
            'post'   : loadStandardTemplate('post'),
            'put'    : loadStandardTemplate('put'),
            'delete' : loadStandardTemplate('delete')
        ]
    }

    private def loadClass(file) {
        gcl.parseClass(file).newInstance()
    }

    private def loadSpec(resName) {
        def f = new File("${cfg.resourcesDir}/${resName}Spec.groovy")
        if (f.exists()) {
            return loadClass(f)
        }

        return [ spec: { s ->
            log.info "No spec overrides defined for $resName. Standard (dummy) text will be used."
            s
        }, schema: {
            log.warn "No schema defined for resource $resName. References in swagger will be broken."
            null
        } ]
    }

    private def containsMethod(obj, methodName) {
        obj.metaClass.respondsTo obj, methodName
    }

    private def getPlural(resource) {
        def m = resource =~ /(.*)(Resource.*)/
        def name = m[0][1]
        name = name[0].toLowerCase() + name.substring(1)
        name.plural()
    }

    private def getCollectionName(resource) {
        if (containsMethod(resource, 'plural')) {
            resource.plural()
        }
        else {
            getPlural resource
        }
    }

    private def loadResource(resFile, map) {
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

    private def loadFiles() {
        def map = [:]
        new File(cfg.resourcesDir).eachFileRecurse (FileType.FILES) {
            if ( it.name.endsWith('Resource.groovy')) {
                // to-do: remove this side effect later on
                loadResource(it, map)
            }
        }
        map
    }

    private def registerResourceAction(resourceDesc, action) {
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

    private def registerActions(resourceDesc) {
        ['get', 'post', 'put', 'delete', 'getById'].each { action ->
            if (containsMethod(resourceDesc.handler, action)) {
                registerResourceAction resourceDesc, action
            }
        }
    }

    private def scaffoldTemplate(template, binding) {
        def tpl = standardTemplates[template].make(binding)
        def finalText = tpl.toString()
        new groovy.json.JsonSlurper().parseText(finalText)
    }

    private def registerSpecs(resourceDesc, doc) {
        doc.definitions["${resourceDesc.name}"] = resourceDesc.specs.schema()
        if (!resourceDesc.actions.any()) {
            return
        }
        doc.paths["/${resourceDesc.plural}"] = [:]
        resourceDesc.actions.each {
            def s = scaffoldTemplate(it.key, ['plural': resourceDesc.plural, 'resource': resourceDesc.name, 'ref': '$ref' ])
            doc.paths["/${resourceDesc.plural}"]["${it.key}"] = s
        }
    }

    def registerResources() {
        def map = loadFiles()
        def doc = scaffoldTemplate('api', apiCfg)

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
}