import java.net.URI
import java.net.http.{HttpRequest, HttpResponse, HttpClient}
import scala.util.Using

def verifyResourceContains(path: String, expectedStatus: Int, expectedBody: Option[String]) = Using(HttpClient.newHttpClient()) { client =>
  val request = HttpRequest.newBuilder.uri(new URI("http://localhost:9000" + path)).GET.build
  val response = client.send(request, HttpResponse.BodyHandlers.ofString)
  assert(response.statusCode == expectedStatus)
}

enablePlugins(LiveReloadPlugin)

scalaVersion := "2.13.16"
resolvers += Resolver.mavenLocal
libraryDependencies ++= Seq(
  "com.lihaoyi" %% "cask" % "0.9.7"
)

InputKey[Unit]("verifyResourceContains") := {
  val args = Def.spaceDelimited("<path> <status> <words> ...").parsed
  val path :: status :: assertions = args
  verifyResourceContains(path, status.toInt, assertions.headOption)
}
