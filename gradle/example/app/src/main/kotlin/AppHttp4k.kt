import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {
    val endpoints =
        listOf(
            "/greet" bind Method.GET to { Response(OK).body(Text.RESPONSE + "!") },
            "/health" bind Method.GET to { Response(OK) },
        )

    routes(endpoints).asServer(SunHttp(8081)).use {
        it.start()
        it.block()
    }
}
