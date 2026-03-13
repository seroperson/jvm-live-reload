package me.seroperson.reload.live.sbt

import java.net.HttpURLConnection
import java.net.URI
import org.scalatest.funsuite.AnyFunSuite
import sbt.testing.framework._
import scala.util.Try

class Http4sTest extends AnyFunSuite {

  private val pluginVersion = sys.props("project.version")
  private val proxyPort = 9001

  private def httpGet(path: String): (Int, String) = {
    val url = new URI(s"http://localhost:$proxyPort/$path").toURL
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    conn.setConnectTimeout(5000)
    conn.setReadTimeout(5000)
    try {
      val status = conn.getResponseCode
      val body = Try {
        val is = if (status >= 400) conn.getErrorStream else conn.getInputStream
        scala.io.Source.fromInputStream(is).mkString
      }.getOrElse("")
      (status, body)
    } finally {
      conn.disconnect()
    }
  }

  private def waitForServer(
      maxAttempts: Int = 60,
      delayMs: Long = 1000
  ): Unit = {
    var attempts = 0
    var ready = false
    while (!ready && attempts < maxAttempts) {
      try {
        httpGet("health")
        ready = true
      } catch {
        case _: Exception =>
          attempts += 1
          Thread.sleep(delayMs)
      }
    }
    assert(ready, s"Server did not become ready after $maxAttempts attempts")
  }

  private def waitForReload(
      path: String,
      expectedStatus: Int,
      expectedBody: String,
      maxAttempts: Int = 60,
      delayMs: Long = 1000
  ): Unit = {
    var attempts = 0
    var matched = false
    while (!matched && attempts < maxAttempts) {
      try {
        val (status, body) = httpGet(path)
        if (status == expectedStatus && body == expectedBody) {
          matched = true
        } else {
          attempts += 1
          Thread.sleep(delayMs)
        }
      } catch {
        case _: Exception =>
          attempts += 1
          Thread.sleep(delayMs)
      }
    }
    assert(
      matched,
      s"Expected $expectedStatus '$expectedBody' at /$path after $maxAttempts attempts"
    )
  }

  test("http4s live reload detects file changes") {
    val runner = SbtRunner
      .inTemp()
      .withDirectoryFromResources("sbt-tests/http4s")
      .withBuildFile(
        s"""|val Http4sVersion = "0.23.30"
            |
            |enablePlugins(LiveReloadPlugin)
            |enablePlugins(BuildInfoPlugin)
            |
            |scalaVersion := "2.13.16"
            |resolvers += Resolver.mavenLocal
            |libraryDependencies ++= Seq(
            |  "org.http4s" %% "http4s-ember-server" % Http4sVersion,
            |  "org.http4s" %% "http4s-dsl" % Http4sVersion,
            |  "org.typelevel" %% "cats-effect" % "3.6.3"
            |)
            |
            |val port = settingKey[Int]("port")
            |port := 8081
            |
            |liveDevSettings := Seq(
            |  DevSettingsKeys.LiveReloadProxyHttpPort -> "$proxyPort",
            |  DevSettingsKeys.LiveReloadHttpPort -> port.value.toString,
            |  DevSettingsKeys.LiveReloadIsDebug -> "true"
            |)
            |
            |buildInfoKeys := Seq[BuildInfoKey](port)
            |buildInfoPackage := "me.seroperson"
            |""".stripMargin
      )
      .withSourceFile(
        "project/plugins.sbt",
        s"""|updateOptions := updateOptions.value.withLatestSnapshots(false)
            |
            |resolvers += Resolver.mavenLocal
            |
            |addSbtPlugin("me.seroperson" % "sbt-live-reload" % "$pluginVersion")
            |addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
            |""".stripMargin
      )
      .build()

    try {
      runner.run("bgRun")

      waitForServer()

      val (greetStatus, greetBody) = httpGet("greet")
      assert(greetStatus == 200, s"Expected 200, got $greetStatus")
      assert(
        greetBody == "Hello World",
        s"Expected 'Hello World', got '$greetBody'"
      )

      runner.copyFile("changes/App.scala", "src/main/scala/App.scala")

      waitForReload("greet_reloaded", 200, "World Hello")

      val (oldStatus, oldBody) = httpGet("greet")
      assert(
        oldStatus == 404,
        s"Expected 404 for /greet after reload, got $oldStatus"
      )
      assert(oldBody == "Not found", s"Expected 'Not found', got '$oldBody'")
    } finally {
      runner.close()
    }
  }

}
