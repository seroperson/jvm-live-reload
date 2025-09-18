lazy val root = (project in file("."))
  .enablePlugins(LiveReloadPlugin)
  .settings(
    scalaVersion := "2.13.16",
    resolvers += Resolver.mavenLocal,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "cask" % "0.9.7"
    ),
    InputKey[Unit]("verifyResourceContains") := {
      val args = Def.spaceDelimited("<path> <status> <words> ...").parsed
      val path :: status :: assertions = args
      ScriptedTools.verifyResourceContains(path, status.toInt, assertions)
    }
  )
