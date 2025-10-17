import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    embeddedServer(Netty, port = 8081) {
        routing {
            get("/greet") { call.respondText("Hello World") }
            get("/health") { call.respond(HttpStatusCode.OK, null) }
        }
    }.start(wait = true)
}
