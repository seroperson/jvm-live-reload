import zio._
import zio.http._

object GreetingServer extends ZIOAppDefault {
  val routes =
    Routes(
      Method.GET / "greet" -> handler { (req: Request) =>
        val name = req.queryOrElse("name", "World")
        Response.text(s"Hello $name!")
      }
    )
  def run = Server.serve(routes).provide(Server.default)
}
