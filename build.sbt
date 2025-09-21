lazy val scala212 = "2.12.20"
lazy val scala213 = "2.13.16"
lazy val scala3 = "3.7.2"
lazy val supportedScalaVersions = List(scala212, scala213, scala3)
lazy val supportedScalaSbtVersions = List(scala212, scala3)

version := "0.0.1"
organization := "me.seroperson"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-encoding",
  "utf8"
) ++
  (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => Seq("-Xsource:3")
    case _            => Seq.empty
  })

javacOptions ++= Seq("-encoding", "UTF-8", "-Xlint:-options")

// Publishing settings
licenses := List("MIT" -> url("https://opensource.org/licenses/MIT"))
homepage := Some(url("https://github.com/seroperson/jvm-live-reload"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/seroperson/jvm-live-reload"),
    "scm:git@github.com:seroperson/jvm-live-reload.git"
  )
)
developers := List(
  Developer(
    id = "seroperson",
    name = "Daniil Sivak",
    email = "seroperson@gmail.com",
    url = url("https://seroperson.me/")
  )
)

// Don't publish documentation
Compile / doc / sources := Seq.empty
Compile / packageDoc / publishArtifact := false

lazy val javaProjectSettings = Seq(
  crossScalaVersions := List(scala212),
  crossPaths := false,
  autoScalaLibrary := false
)

lazy val `sbt-live-reload` = (projectMatrix in file("sbt"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-live-reload",
    description := "Providing an universal Live Reload expirience for web applications built with SBT",
    scriptedBufferLog := false,
    (pluginCrossBuild / sbtVersion) := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.10.10"
        case _      => "2.0.0-RC4"
      }
    }
  )
  .jvmPlatform(scalaVersions = supportedScalaSbtVersions)
  .dependsOn(`build-link`)
  .dependsOn(`runner`)

lazy val `webserver` = (project in file("core/webserver"))
  .settings(javaProjectSettings)
  .settings(
    name := "jvm-live-reload-webserver",
    description := "Development-mode webserver for Live Reload expirience on JVM",
    libraryDependencies ++= Seq(
      "io.undertow" % "undertow-core" % "2.1.0.Final"
    )
  )
  .dependsOn(`build-link`)

lazy val `runner` = (project in file("core/runner"))
  .settings(javaProjectSettings)
  .settings(
    name := "jvm-live-reload-runner",
    description := "Runner",
    libraryDependencies += "org.playframework" % "play-file-watch" % "3.0.0-M4"
  )
  .dependsOn(`build-link`)

lazy val `build-link` = (project in file("core/build-link"))
  .settings(javaProjectSettings)
  .settings(
    name := "jvm-live-reload-build-link",
    description := "Build link"
  )

lazy val `hooks` = (projectMatrix in file("core/hooks"))
  .settings(
    name := "jvm-live-reload-hooks",
    description := "Hooks",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-http" % "3.3.3" % Provided,
      "org.typelevel" %% "cats-effect" % "3.6.3" % Provided
    )
  )
  .jvmPlatform(scalaVersions = supportedScalaVersions)
  .dependsOn(`build-link`)
