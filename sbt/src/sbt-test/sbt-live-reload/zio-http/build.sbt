lazy val root = (project in file("."))
  .enablePlugins(LiveReloadPlugin)
  .settings(
    resolvers += Resolver.mavenLocal,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-http" % "3.3.3",
      "org.slf4j" % "slf4j-simple" % "2.0.16"
    ),
    InputKey[Unit]("verifyResourceContains") := {
      val args = Def.spaceDelimited("<path> <status> <words> ...").parsed
      val path :: status :: assertions = args
      ScriptedTools.verifyResourceContains(path, status.toInt, assertions)
    }
  )
