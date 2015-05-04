class PersonSpec {
    def schema() {
        [
                properties: [
                        id     : [type: "string"],
                        name   : [type: "string"]
                ],
                required: ["id", "name"]
        ]
    }
}
