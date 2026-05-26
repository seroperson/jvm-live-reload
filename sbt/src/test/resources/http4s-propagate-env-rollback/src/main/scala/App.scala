import cats.effect.Async
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Sync
import cats.syntax.all._
import com.comcast.ip4s._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._

object App extends IOApp.Simple {

  def helloWorldRoutes: HttpRoutes[IO] = {
    val dsl = new Http4sDsl[IO] {}
    import dsl._
    HttpRoutes.of[IO] {
      case GET -> Root / "greet" =>
        Ok(sys.env.getOrElse("JLR_LEAK_CHECK", "<absent>"))
      case GET -> Root / "health" =>
        Ok()
    }
  }

  def runServer: IO[Nothing] = {
    for {
      _ <-
        EmberServerBuilder
          .default[IO]
          .withPort(Port.fromInt(me.seroperson.BuildInfo.port).get)
          .withHttpApp(helloWorldRoutes.orNotFound)
          .build
    } yield ()
  }.useForever

  val run = runServer
}
