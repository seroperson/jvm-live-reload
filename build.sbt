lazy val publishSettings = Seq(
  version := "0.0.1",
  organization := "me.seroperson",

  // Publishing settings
  licenses := List("MIT" -> url("https://opensource.org/licenses/MIT")),
  homepage := Some(url("https://github.com/seroperson/jvm-live-reload")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/seroperson/jvm-live-reload"),
      "scm:git@github.com:seroperson/jvm-live-reload.git"
    )
  ),
  developers := List(
    Developer(
      id = "seroperson",
      name = "Daniil Sivak",
      email = "seroperson@gmail.com",
      url = url("https://seroperson.me/")
    )
  ),
  publishLocalConfiguration := publishLocalConfiguration.value
    .withOverwrite(true),
  sources in (Compile, doc) := Seq.empty,
  publishArtifact in (Compile, packageDoc) := false
)

lazy val root = (project in file("."))
  .settings(publish / skip := true)
  .aggregate(`sbt-live-reload`, `webserver`, `build-link`, `hooks`, `runner`)

lazy val `sbt-live-reload` = (project in file("sbt"))
  .enablePlugins(SbtPlugin)
  .settings(publishSettings)
  .settings(
    name := "sbt-live-reload",
    description := "Providing an universal Live Reload expirience for web applications built with SBT",
    sbtPlugin := true,
    scriptedBufferLog := false
  )
  .dependsOn(`build-link`)
  .dependsOn(`runner`)

lazy val `webserver` = (project in file("core/webserver"))
  .settings(publishSettings)
  .settings(
    name := "jvm-live-reload-webserver",
    description := "Development-mode webserver for Live Reload expirience on JVM",
    libraryDependencies ++= Seq(
      "io.undertow" % "undertow-core" % "2.1.0.Final"
    )
  )
  .dependsOn(`build-link`)
  .dependsOn(`hooks`)

lazy val `runner` = (project in file("core/runner"))
  .settings(publishSettings)
  .settings(
    name := "jvm-live-reload-runner",
    description := "Runner",
    libraryDependencies += "org.playframework" %% "play-file-watch" % "2.0.1"
  )
  .dependsOn(`build-link`)

lazy val `hooks` = (project in file("core/hooks"))
  .settings(publishSettings)
  .settings(
    name := "jvm-live-reload-hooks",
    description := "Hooks",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-http" % "3.3.3" % Provided
    )
  )
  .dependsOn(`build-link`)

lazy val `build-link` = (project in file("core/build-link"))
  .settings(publishSettings)
  .settings(
    name := "jvm-live-reload-build-link",
    description := "Build link"
  )
