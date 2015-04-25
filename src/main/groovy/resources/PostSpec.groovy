class PostSpec {
	def schema() {
		[
			discriminator: "petType",
			properties: [
				name   : [type: "string"],
				petType: [type: "string"]
			],
			required: ["name", "petType"]
		]
	}

	def spec(specs) {
		specs['get'].description = "Overwritten description for the get operation."
	}
}
