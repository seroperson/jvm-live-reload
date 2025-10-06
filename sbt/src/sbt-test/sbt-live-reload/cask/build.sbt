enablePlugins(LiveReloadPlugin)

scalaVersion := "2.13.16"
resolvers += Resolver.mavenLocal
libraryDependencies ++= Seq(
  "com.lihaoyi" %% "cask" % "0.9.7"
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
