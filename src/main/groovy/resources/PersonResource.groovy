class PersonResource {
	def get(req, resp) {
		'Hello!'
	}

	def post(req, resp) {
		'Data posted!'
	}

	def getById(req, resp) {
		return '{id:"1"}'
	}

	def patch(req, resp) {
		'Data patched!'
	}

	def plural() { 'people' }

	def skipAuthFor() { ['get', 'post']}
}