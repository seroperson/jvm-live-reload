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

  def helloWorldRoutes[F[_]: Sync]: HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "greet" =>
        Ok("Hello World")
      case GET -> Root / "health" =>
        Ok()
    }
  }

  def runServer[F[_]: Async]: F[Nothing] = {
    val httpApp = helloWorldRoutes[F].orNotFound
    for {
      _ <-
        EmberServerBuilder
          .default[F]
          .withHost(ipv4"0.0.0.0")
          .withPort(Port.fromInt(me.seroperson.BuildInfo.port).get)
          .withHttpApp(httpApp)
          .build
    } yield ()
  }.useForever

  val run = runServer[IO]
}
