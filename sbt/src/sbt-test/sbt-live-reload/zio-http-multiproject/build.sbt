resolvers += Resolver.mavenLocal

val isSbt2 = settingKey[Boolean]("isSbt2")
ThisBuild / isSbt2 := (sbtBinaryVersion.value match {
  case "2" => true
  case _   => false
})

val proxyPort = settingKey[Int]("proxyPort")
ThisBuild / proxyPort := (if (isSbt2.value) 9001 else 9000)

val port = settingKey[Int]("port")
ThisBuild / port := (if (isSbt2.value) 8081 else 8080)

lazy val `project-a` = (project in file("project-a"))
  .enablePlugins(LiveReloadPlugin)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-http" % "3.5.1",
      "org.slf4j" % "slf4j-simple" % "2.0.16"
    ),

    liveDevSettings := Seq(
      DevSettingsKeys.LiveReloadProxyHttpPort -> proxyPort.value.toString,
      DevSettingsKeys.LiveReloadHttpPort -> port.value.toString
    ),

    buildInfoKeys := Seq[BuildInfoKey](port),
    buildInfoPackage := "me.seroperson",
  )
  .dependsOn(`project-b`)

lazy val `project-b` = (project in file("project-b"))

lazy val root = (project in file("."))

root / InputKey[Unit]("verifyResourceContains") := {
  import sttp.client4.quick._
  import sttp.client4.Response
  import com.eed3si9n.expecty.Expecty.assert

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
