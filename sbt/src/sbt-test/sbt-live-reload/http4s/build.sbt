import java.net.URI
import java.net.http.{HttpRequest, HttpResponse, HttpClient}
import scala.util.Using

val Http4sVersion = "0.23.30"

def verifyResourceContains(path: String, expectedStatus: Int, expectedBody: Option[String]) = Using(HttpClient.newHttpClient()) { client =>
  val request = HttpRequest.newBuilder.uri(new URI("http://localhost:9000" + path)).GET.build
  val response = client.send(request, HttpResponse.BodyHandlers.ofString)
  assert(response.statusCode == expectedStatus)
}

enablePlugins(LiveReloadPlugin)

scalaVersion := "2.13.16"
resolvers += Resolver.mavenLocal
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-server" % Http4sVersion,
  "org.http4s" %% "http4s-dsl" % Http4sVersion,
  "org.typelevel" %% "log4cats-slf4j" % "2.7.1",
  "org.slf4j" % "slf4j-simple" % "2.0.16"
  // "org.typelevel" %% "cats-effect" % "3.7-083a635-20250918T210328Z-SNAPSHOT"
)

InputKey[Unit]("verifyResourceContains") := {
  val args = Def.spaceDelimited("<path> <status> <words> ...").parsed
  val path :: status :: assertions = args
  verifyResourceContains(path, status.toInt, assertions.headOption)
}
