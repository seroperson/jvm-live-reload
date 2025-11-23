import zio._
import zio.http._

object GreetingServer extends ZIOAppDefault {
  val routes =
    Routes(
      Method.GET / "greet" -> handler { (req: Request) =>
        Response.text(Text.response)
      },
      Method.GET / "health" -> handler { (req: Request) =>
        Response.ok
      }
    )
  def run = Server
    .serve(routes)
    .provide(Server.defaultWithPort(8080))
}
