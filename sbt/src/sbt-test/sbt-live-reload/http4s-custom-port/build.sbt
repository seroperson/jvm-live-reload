import java.net.URI
import java.net.http.{HttpRequest, HttpResponse, HttpClient}
import scala.util.Using

val Http4sVersion = "0.23.30"

def verifyResourceContains(path: String, expectedStatus: Int, expectedBody: Option[String]) = Using(HttpClient.newHttpClient()) { client =>
  val request = HttpRequest.newBuilder.uri(new URI("http://localhost:9001" + path)).GET.build
  val response = client.send(request, HttpResponse.BodyHandlers.ofString)
  val body = response.body()
  if (body != expectedBody.getOrElse("")) {
    sys.error(s"Body doesn't match: ${body} != ${expectedBody.getOrElse("")}")
  }
  if (response.statusCode != expectedStatus) {
    sys.error(s"Status doesn't match: ${response.statusCode} != ${expectedStatus}")
  }
}.get

enablePlugins(LiveReloadPlugin)

scalaVersion := "2.13.16"
resolvers += Resolver.mavenLocal
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-server" % Http4sVersion,
  "org.http4s" %% "http4s-dsl" % Http4sVersion,
  "org.typelevel" %% "log4cats-slf4j" % "2.7.1",
  "org.slf4j" % "slf4j-simple" % "2.0.16"
)
liveDevSettings ++= Seq(
  "live.reload.proxy.http.port" -> "9001",
  "live.reload.http.port" -> "8081"
)

InputKey[Unit]("verifyResourceContains") := {
  val args = Def.spaceDelimited("<path> <status> <words> ...").parsed
  val path :: status :: assertions = args
  println(s"Requesting $path with $status with $assertions with $args")
  verifyResourceContains(path, status.toInt, assertions.headOption)
}
