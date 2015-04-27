class PostSpec {
	def schema() {
		[
			properties: [
				id     : [type: "string"],
				title  : [type: "string"],
				petType: [type: "string"]
			],
			discriminator: "petType",
			required: ["name", "petType"]
		]
	}

	def overrideSpec(specs) {
		specs.resourceSpec['get'].description = "Overwritten description for the get operation."
	}
}
