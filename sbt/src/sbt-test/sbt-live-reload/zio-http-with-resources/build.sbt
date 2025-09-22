import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import scala.util.Using

def verifyResourceContains(
    path: String,
    expectedStatus: Int,
    expectedBody: Option[String]
) = Using(HttpClient.newHttpClient()) { client =>
  val request = HttpRequest.newBuilder
    .uri(new URI("http://localhost:9000" + path))
    .GET
    .build
  val response = client.send(request, HttpResponse.BodyHandlers.ofString)
  val body = response.body()
  if (body != expectedBody.getOrElse("")) {
    sys.error(s"Body doesn't match: ${body} != ${expectedBody.getOrElse("")}")
  }
  if (response.statusCode != expectedStatus) {
    sys.error(
      s"Status doesn't match: ${response.statusCode} != ${expectedStatus}"
    )
  }
}.get

enablePlugins(LiveReloadPlugin)

scalaVersion := "2.13.16"

resolvers += Resolver.mavenLocal
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-http" % "3.3.3",
  "dev.zio" %% "zio-config" % "4.0.4",
  "dev.zio" %% "zio-config-magnolia" % "4.0.4",
  "dev.zio" %% "zio-config-typesafe" % "4.0.4",
  "org.slf4j" % "slf4j-simple" % "2.0.16"
)

InputKey[Unit]("verifyResourceContains") := {
  val args = Def.spaceDelimited("<path> <status> <words> ...").parsed
  val path :: status :: assertions = args
  verifyResourceContains(path, status.toInt, assertions.headOption)
}
