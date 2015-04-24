import groovy.io.FileType
import static spark.Spark.*
import spark.*
import groovy.json.JsonOutput


class ResourceLoader {
    def gcl = new GroovyClassLoader()

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

    def registerAction(resource, action) {
        def col = getCollectionName(resource)
        def restAction = action
        def path = "/$col"
        if (action == 'getById') {
            restAction = "get"
            path += '/:id'
        }

        spark.Spark."$restAction"(path, { req, res -> resource."$action"(req, res) } )

        println "Registering $col.$action"
    }

    def loadResource(resFile, map) {
        def resName = (resFile.name =~ /(.*)Resource.groovy/)[0][1]
        def h = loadClass(resFile)
        def s = loadSpec(resName)
        map."$resName" = [
            handler: h,
            plural: getCollectionName(h),
            specs: s,
            // todo: better initialization here
            actions: new LinkedHashMap()
        ]
    }

    def loadFiles(dirPath) {
        // todo: proper map initialization here
        def map = new LinkedHashMap()
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
        
    }

    def registerActions(resourceDesc) {
        ['get', 'post', 'put', 'delete', 'getById'].each { action ->
            if (containsMethod(resourceDesc.handler, action)) {
                registerResourceAction resourceDesc, action
            }
        }
    }

    def registerResources(dirPath) {
        def map = loadFiles(dirPath)
        map.each {
            registerActions it.value
        }
    }

    def registerResource(resource) {
        ['get', 'post', 'put', 'delete', 'getById'].each { action ->
            if (containsMethod(resource, action)) {
                registerAction resource, action
            }
        }
    }

    def loadDir(dirPath, fileFiler= null) {
        def list = []

        def filter = fileFiler ?: { it.name.endsWith '.groovy' }

        new File(dirPath).eachFileRecurse (FileType.FILES) {
            if (filter(it) ) {
                list << loadClass(it)
            }
        }

        list
    }


    def loadResources(dirPath = 'resources') {
        def l = loadDir dirPath, { it.name.endsWith 'Resource.groovy' }
        l.each { registerResource it }
        return l
    }

    //todo: and the need for refactoring just increases...
    def getSpecRes(spec) {
        (spec =~ /(.*)Spec.*/)[0][1]
    }

    // todo: this whole plural is looking crappy right now. Refactor ASAP.
    def getSpecPlural(spec, resources) {
        def resName = getSpecRes(spec)
        def res = resources.find { it =~ "^${resName}.*" }
        getPlural(res)
    }

    def registerSpec(spec, resources) {
        [ "/${getSpecPlural(spec, resources)}": spec.schema() ]
    }

    def resolveTemplate(templateName, binding) {
        def baseText = new File("templates/${templateName}.template.json").text
        def engine = new groovy.text.SimpleTemplateEngine()
        def template = engine.createTemplate(baseText).make(binding)
        def finalText = template.toString()
        new groovy.json.JsonSlurper().parseText(finalText)
    }

    def registerActions(spec, resources) {
        def pl = getSpecPlural(spec, resources)
        def getSpec = resolveTemplate('getSpec', [ 'plural': pl, 'resource': getSpecRes(spec), 'ref': '$ref' ])
        def actionsSpec = [ 'get': getSpec ]
        spec.spec(actionsSpec)
        actionsSpec
    }

    def loadSpecs(resources, dirPath = 'resources') {
        def specs = resolveTemplate('apiSpec', ['host': 'localhost', 'version': '0.1', 'description':'api description', 'title':'api title'])
        def resSpecs = loadDir(dirPath, { it.name.endsWith 'Spec.groovy' })
        specs.definitions << resSpecs.collect { registerSpec it, resources }
        specs.paths << resSpecs.collect { registerActions it, resources }

        spark.Spark.get "/swagger", { req, res ->
            res.header("Access-Control-Allow-Origin", '*');
            res.header("Access-Control-Request-Method", '*');
            res.header("Access-Control-Allow-Headers", '*');

            JsonOutput.toJson(specs)
        }
    }

    def loadAllResources(){
        def res = loadResources()
        loadSpecs(res)
        registerResources('resources')
    }
}