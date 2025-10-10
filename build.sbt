lazy val scala212 = "2.12.20"
lazy val scala213 = "2.13.16"
lazy val scala3 = "3.7.2"
lazy val supportedScalaVersions = List(scala212, scala213, scala3)
lazy val supportedScalaSbtVersions = List(scala212, scala3)

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

// Publishing settings
organization := "me.seroperson"
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

lazy val javaProjectSettings = Seq(
  crossScalaVersions := List(scala212),
  crossPaths := false,
  autoScalaLibrary := false
)

lazy val `sbtLiveReload` = (projectMatrix in file("sbt"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-live-reload",
    description := "Providing an universal Live Reload experience for web applications built with SBT",
    scriptedBufferLog := false,
    scriptedBatchExecution := false,
    (pluginCrossBuild / sbtVersion) := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.10.10"
        case _      => "2.0.0-RC4"
      }
    }
  )
  .jvmPlatform(scalaVersions = supportedScalaSbtVersions)
  .dependsOn(`buildLink`)
  .dependsOn(`runner`)

lazy val `webserver` = (project in file("core/webserver"))
  .settings(javaProjectSettings)
  .settings(
    name := "jvm-live-reload-webserver",
    description := "Development-mode proxy webserver for Live Reload experience on JVM",
    libraryDependencies ++= Seq(
      "io.undertow" % "undertow-core" % "2.3.19.Final"
    )
  )
  .dependsOn(`buildLink`)

lazy val `runner` = (project in file("core/runner"))
  .settings(javaProjectSettings)
  .settings(
    name := "jvm-live-reload-runner",
    description := "Contains an universal Live Reload webserver initialization and reloading logic",
    libraryDependencies += "org.playframework" % "play-file-watch" % "3.0.0-M4"
  )
  .dependsOn(`buildLink`)

lazy val `buildLink` = (project in file("core/build-link"))
  .settings(javaProjectSettings)
  .settings(
    name := "jvm-live-reload-build-link",
    description := "Contains classes which shared between build system and application runtime"
  )

lazy val `hookScala` = (projectMatrix in file("core/hook-scala"))
  .settings(
    name := "jvm-live-reload-hook-scala",
    description := "Predefined set of hooks for popular Scala webframeworks",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-http" % "3.5.1" % Provided,
      "org.typelevel" %% "cats-effect" % "3.6.3" % Provided
    )
  )
  .jvmPlatform(scalaVersions = supportedScalaVersions)
  .dependsOn(`buildLink`)
