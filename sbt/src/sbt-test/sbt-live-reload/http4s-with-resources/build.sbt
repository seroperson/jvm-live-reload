val Http4sVersion = "0.23.30"

enablePlugins(LiveReloadPlugin)

scalaVersion := "2.13.16"
resolvers += Resolver.mavenLocal
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-server" % Http4sVersion,
  "org.http4s" %% "http4s-dsl" % Http4sVersion,
  "org.typelevel" %% "log4cats-slf4j" % "2.7.1",
  "org.slf4j" % "slf4j-simple" % "2.0.16",
  "com.github.pureconfig" %% "pureconfig" % "0.17.9"
)

InputKey[Unit]("verifyResourceContains") := {
  import sttp.client4.quick._
  import sttp.client4.Response

  val args = Def.spaceDelimited("<path> <status> <words> ...").parsed
  val path :: status :: assertions = args

  val response: Response[String] = quickRequest
    .get(uri"http://localhost:9000/${path}")
    .send()

  assert(response.code.code.toString == status)
  assertions.foreach { v =>
    assert(response.body == v)
  }
}
