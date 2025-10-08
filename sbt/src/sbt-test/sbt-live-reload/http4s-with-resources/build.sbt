val Http4sVersion = "0.23.30"

enablePlugins(LiveReloadPlugin)
enablePlugins(BuildInfoPlugin)

scalaVersion := "2.13.16"
resolvers += Resolver.mavenLocal
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-server" % Http4sVersion,
  "org.http4s" %% "http4s-dsl" % Http4sVersion,
  "org.typelevel" %% "log4cats-slf4j" % "2.7.1",
  "org.slf4j" % "slf4j-simple" % "2.0.16",
  "com.github.pureconfig" %% "pureconfig" % "0.17.9"
)

val isSbt2 = settingKey[Boolean]("isSbt2")
isSbt2 := (sbtBinaryVersion.value match {
  case "2" => true
  case _   => false
})

val proxyPort = settingKey[Int]("proxyPort")
proxyPort := (if (isSbt2.value) 9001 else 9000)

val port = settingKey[Int]("port")
port := (if (isSbt2.value) 8081 else 8080)

liveDevSettings := Seq(
  "live.reload.proxy.http.port" -> proxyPort.value.toString,
  "live.reload.http.port" -> port.value.toString
)

buildInfoKeys := Seq[BuildInfoKey](port)
buildInfoPackage := "me.seroperson"

InputKey[Unit]("verifyResourceContains") := {
  import sttp.client4.quick._
  import sttp.client4.Response

  val args = Def.spaceDelimited("<path> <status> <words> ...").parsed
  val path :: status :: assertions = args

  val response: Response[String] = quickRequest
    .get(uri"http://localhost:${proxyPort.value}/${path}")
    .send()

  assert(response.code.code.toString == status)
  assertions.foreach { v =>
    assert(response.body == v)
  }
}
