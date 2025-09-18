val Http4sVersion = "0.23.30"
// val Http4sVersion = "0.23.30-156-aa5a5ea-20250918T205245Z-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(LiveReloadPlugin)
  .settings(
    scalaVersion := "2.13.16",
    resolvers += Resolver.mavenLocal,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % Http4sVersion,
      "org.http4s" %% "http4s-dsl" % Http4sVersion,
      "org.typelevel" %% "log4cats-slf4j" % "2.7.1",
      "org.slf4j" % "slf4j-simple" % "2.0.16"
      // "org.typelevel" %% "cats-effect" % "3.7-083a635-20250918T210328Z-SNAPSHOT"
    ),
    InputKey[Unit]("verifyResourceContains") := {
      val args = Def.spaceDelimited("<path> <status> <words> ...").parsed
      val path :: status :: assertions = args
      ScriptedTools.verifyResourceContains(path, status.toInt, assertions)
    }
  )
