import zio._
import zio.config.magnolia._
import zio.config.typesafe._
import zio.http._
import scala.io.Source

case class TestRootConfig(a: Int)

object TestRootConfig {
  implicit lazy val d = zio.config.magnolia.DeriveConfig[TestRootConfig]
}

object GreetingServer extends ZIOAppDefault {

  val routes =
    Routes(
      Method.GET / "greet" -> handler { (req: Request) =>
        for {
          config <- ZIO.service[TestRootConfig]
        } yield Response.text(s"Hello World ${config.a}")
      },
      Method.GET / "health" -> handler { (req: Request) =>
        Response.ok
      }
    )

  def run = Server
    .serve(routes)
    .provide(
      Server.default,
      ZLayer.fromZIO {
        TypesafeConfigProvider
          .fromResourcePath()
          .load(TestRootConfig.d.desc)
      }
    )
}
