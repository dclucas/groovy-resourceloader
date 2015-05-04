class CommentSpec {
    def schema() {
        [
                properties: [
                        id       : [type: "string"],
                        contents : [type: "string"]
                ],
                required: ["id", "contents"]
        ]
    }
}
