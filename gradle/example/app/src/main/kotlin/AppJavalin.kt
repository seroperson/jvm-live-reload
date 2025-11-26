import io.javalin.Javalin

fun main() {
    val server =
        Javalin.create().get("/greet") { it.result(Text.RESPONSE) }.get("/health") { it.status(200) }
    try {
        server.start(8081)
        Thread.currentThread().join()
    } catch (ex: InterruptedException) {
        server.stop()
    }
}
