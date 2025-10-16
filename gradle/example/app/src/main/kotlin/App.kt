import org.http4k.core.*
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.*
import org.http4k.server.*

fun main() {
    val endpoints =
        listOf(
            "/greet" bind Method.GET to { Response(OK).body("Hello World") },
            "/health" bind Method.GET to { Response(OK) },
        )

    routes(endpoints).asServer(SunHttp(8081)).use {
        it.start()
        it.block()
    }
}
