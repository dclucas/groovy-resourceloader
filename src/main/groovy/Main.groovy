class Main {
    static void main(args) {
        println 'Starting server...'
        initServer()
    }

    static void initServer() {
        def rs = new ResourceLoader()
        rs.registerResources()
    }
}