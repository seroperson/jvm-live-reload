import zio._
import zio.http._

object GreetingServer extends ZIOAppDefault {
  val routes =
    Routes(
      Method.GET / "greet" -> handler { (req: Request) =>
        Response.text(s"Hello World")
      }
    )
  def run = Server.serve(routes).provide(Server.default)
}
