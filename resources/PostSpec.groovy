class PostSpec {
	def schema(docHelper) {
		[
				discriminator: "petType",
				properties: [
						name   : [type: "string"],
						petType: [type: "string"]
				],
				required: ["name", "petType"]
		]
	}

	def spec(docHelper) {
		["/pets": [
				"get": [
						"description": "Returns all pets from the system that the user has access to",
						"produces"   : [
								"application/json"
						],
						"responses"  : [
								"200": [
										"description": "A list of pets.",
										"schema"     : [
												"type" : "array",
												"items": [
														'$ref': "#/definitions/pet"
												]
										]
								]
						]
				]
		]]
	}
}
