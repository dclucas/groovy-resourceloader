import groovy.io.FileType
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.text.SimpleTemplateEngine
import groovy.util.logging.Slf4j

import com.fasterxml.jackson.databind.ObjectMapper

import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.main.JsonSchemaFactory

import spark.*
import static spark.Spark.*

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
            'patch'    : loadStandardTemplate('patch'),
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

        return [ overrideSpec: { s ->
            log.info "No spec overrides defined for $resName. Standard (dummy) text will be used."
            s
        }, schema: {
            log.warn "No schema defined for resource $resName. References in swagger will be broken."
            []
        } ]
    }

    private def containsMethod(obj, methodName) {
        return obj.metaClass.respondsTo(obj, methodName)
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
        def col = resourceDesc.plural
        log.info "Registering $col.$action"
        def restAction = action
        def path = "/$col"
        resourceDesc.actions."$action" = action
        switch(action) {
            case 'getById':
                restAction = "get"
                path += '/:id'
                break
            case 'delete':
            case 'patch':
                path += '/:id'
                break
        }

        spark.Spark."$restAction" path, { req, res -> resourceDesc.handler."$action"(req, res) }
    }

    private def registerActions(resourceDesc) {
        ['get', 'post', 'patch', 'delete', 'getById'].each { action ->
            if (containsMethod(resourceDesc.handler, action)) {
                registerResourceAction resourceDesc, action
            }
        }
    }

    private def scaffoldTemplate(template, binding) {
        def tpl = standardTemplates[template].make(binding)
        def finalText = tpl.toString()
        new JsonSlurper().parseText(finalText)
    }

    private def registerSpecs(resourceDesc, doc) {
        doc.definitions["${resourceDesc.name}"] = resourceDesc.specs.schema()
        def resourcePath = "/${resourceDesc.plural}"
        if (resourceDesc.actions.any()) {
            doc.paths[resourcePath] = [:]
            resourceDesc.actions.each {
                def singular = resourceDesc.name[0].toLowerCase() + resourceDesc.name.substring(1)
                def s = scaffoldTemplate(it.key, [
                        'plural'  : resourceDesc.plural,
                        'resource': resourceDesc.name,
                        'singular': singular,
                        'ref'     : '$ref'])

                if (it.key == 'getById') {
                    doc.paths["$resourcePath/${singular}Id"] = ["get": s]
                } else {
                    doc.paths[resourcePath]["${it.key}"] = s
                }
            }
        }

        resourceDesc.specs.overrideSpec([
                fullSpec: doc,
                resourceSpec: doc.paths[resourcePath],
                resourcePath: resourcePath
        ])
    }

    def jsonSchemaFactory = JsonSchemaFactory.byDefault()
    def objectMapper = new ObjectMapper()

    def registerValidators(resourceDesc, doc) {
        def postSchema = jsonSchemaFactory.getJsonSchema(objectMapper.valueToTree(resourceDesc.specs.schema()))
        spark.Spark.before "/${resourceDesc.plural}", {req, res ->
            if (req.requestMethod() == 'POST') {
                // todo: handle parsing errors -- shouldn't they all return a 400?
                def validationResults = postSchema.validate(JsonLoader.fromString(req.body()))
                if (!validationResults.isSuccess()) {
                    halt(400, validationResults.collect({ it.message}).join(','))
                }
            }
         }
    }

    def registerResources() {
        def map = loadFiles()
        def doc = scaffoldTemplate('api', apiCfg)

        map.each {
            registerActions it.value
            registerSpecs it.value, doc
            registerValidators it.value, doc
        }

        spark.Spark.get "/swagger", { req, res ->
            res.header("Access-Control-Allow-Origin", '*');
            res.header("Access-Control-Request-Method", '*');
            res.header("Access-Control-Allow-Headers", '*');

            JsonOutput.toJson(doc)
        }
    }
}