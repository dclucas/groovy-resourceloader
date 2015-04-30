class PostSpec {
	def schema() {
		[
			properties: [
				id     : [type: "string"],
				title  : [type: "string"]
			],
			required: ["id", "title"]
		]
	}

	def overrideSpec(specs) {
		specs.resourceSpec['get'].description = "Overwritten description for the get operation."
	}
}
