val Http4sVersion = "1.0.0-M44"

lazy val root = (project in file("."))
  .enablePlugins(LiveReloadPlugin)
  .settings(
    scalaVersion := "2.13.16",
    resolvers += Resolver.mavenLocal,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % Http4sVersion,
      "org.http4s" %% "http4s-dsl" % Http4sVersion,
      "org.typelevel" %% "log4cats-slf4j" % "2.7.1"
    ),
    InputKey[Unit]("verifyResourceContains") := {
      val args = Def.spaceDelimited("<path> <status> <words> ...").parsed
      val path :: status :: assertions = args
      ScriptedTools.verifyResourceContains(path, status.toInt, assertions)
    }
  )
