@Grab('com.sparkjava:spark-core:2.1')
@Grab('org.millarts:groovy-pluralize-en:0.2.1')
//@Grab('org.atteo:evo-inflector:1.2')

import groovy.io.FileType
import static spark.Spark.*
import spark.*
import groovy.json.JsonOutput
//import org.atteo.evo.inflector.*

def loadClass(file) {
	this.class.classLoader
		.parseClass(file.text)
		.newInstance()	
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

def registerResource(resource) {
	['get', 'post', 'put', 'delete', 'getById'].each { action ->
		if (containsMethod(resource, action)) {
			registerAction resource, action
		}
	}
}

def loadResources(dirPath = 'resources') {
	l = loadDir dirPath, { it.name.endsWith 'Resource.groovy' }
	l.each { registerResource it }
}

def registerSpec(spec) {
	println spec
}

def loadSpecs(dirPath = 'resources') {
	l = loadDir dirPath, { it.name.endsWith 'Spec.groovy' }
	l.each { registerSpec it }
}

// todo: figure out how to turn this into a one argument 'Pet' call
def genJsonSchema(resourceName, targetSchema) {
	def cfg = new ConfigSlurper().parse(targetSchema)
	def jsonSchema = JsonOutput.toJson([
		"definitions": [ 
			"$resourceName" : cfg
		]
	])
	jsonSchema
}

//println genJsonSchema('Pet', PetSchema)

def genGetSchema(collectionName, furtherSpecs  = null) {
		// todo: is it valid to assume that these elements should go in all GET specs?
		// overwriting is easy with the << operator, but removing entries would be tricky
		def coreSchema = [
			"get": [
				"description": "Returns all $collectionName from the system that the user has access to",
				"produces"   : [
					"application/json"
				],
				"responses"  : [
					"200": [
						"description": "A list of $collectionName.",
						"schema"     : [
							"type" : "array",
							"items": [
								"\$ref": "/swagger/$collectionName-definitions.json"
							]
						]
					]
				]
			],
			"foo": "bar"
		]

		if (furtherSpecs) {
			coreSchema.get << furtherSpecs
		}

		schema = [ "/$collectionName": coreSchema ]
		JsonOutput.toJson(schema)
}
loadResources()

loadSpecs()
