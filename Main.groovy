@Grab('com.sparkjava:spark-core:2.1')

import groovy.io.FileType
import static spark.Spark.*

def loadClass(file) {
	this.class.classLoader
		.parseClass(file.text)
		.newInstance()	
}

def loadDir(dirPath, fileFiler= null) {
	def list = []
	
	def filter = fileFiler ?: { it.name.endsWith('.groovy') }

	new File(dirPath).eachFileRecurse (FileType.FILES) { 
		if (filter(it) ) {
			list << loadClass(it)
		}
	}

	list
}

def containsMethod(obj, methodName) {
	obj.metaClass.respondsTo(obj, methodName)
}

def getCollectionName(controller) {
	if (containsMethod(controller, 'getCollectionName')) {
		controller.getCollectionName()
	}
	else {
		def m = controller =~ /(.*)(Controller.*)/
		m[0][1]
	}	
}

def registerAction(controller, action) {
	def col = getCollectionName(controller)
	spark.Spark."$action"("/$col", { req, res -> controller."$action"(req, res) } )
	println "$controller.$action"
}

def registerController(controller) {
	def actions = ['get', 'post', 'put', 'delete']
	actions.each { 
		if (containsMethod(controller, it))
			registerAction controller, it
	}
}

def loadControllers(dirPath = 'controllers') {
	l = loadDir dirPath, { it.name.endsWith('Controller.groovy') }
	l.each { registerController it }
}
/*
class HelloWorld {
    static void main(String[] args) {
        get("/hello", { req, res -> "Hello World" });
    }
}
*/

//spark.Spark.get("/hello", { req, res -> "Hello World" })
loadControllers 'controllers'
