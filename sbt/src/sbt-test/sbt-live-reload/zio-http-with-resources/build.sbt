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
