package me.seroperson.reload.live.mill.test

import mill.testkit.IntegrationTester
import okhttp3.OkHttpClient
import okhttp3.Request
import scala.util.Using
import os.ProcessOutput
import org.scalatest.funsuite.AnyFunSuite

class IntegrationTests extends AnyFunSuite {

  private lazy val client = OkHttpClient()

  private def runUntil(
    url: String,
    expectedStatus: Int,
    expectedBody: String,
  ): Boolean = {
    val request: Request = Request.Builder().url(url).build()

    try {
      val (code, body) = Using(client.newCall(request).execute()) { response =>
        response.code -> response.body.string()
      }.get
      println(s"Requesting $url, got $code and $body")
      if (expectedStatus == code && expectedBody == body) {
        return true
      } else {
        Thread.sleep(500)
        return runUntil(url, expectedStatus, expectedBody)
      }
    } catch {
      case ex: Exception =>
        println(s"Got exception: ${ex.getMessage}")
        Thread.sleep(500)
        return runUntil(url, expectedStatus, expectedBody)
    }
  }

  test("zio-http") {
    val resourceDir = os.Path(BuildInfo.resourceDir) / "zio-http"

    val tester = new IntegrationTester(
      daemonMode = false,
      workspaceSourcePath = resourceDir,
      millExecutable = os.Path(BuildInfo.exePath),
      // debugLog = true
    )

    val runThread = new Thread(new Runnable() {
      override def run(): Unit = {
        tester.eval(
          "app.liveReloadRun",
          env = Map("PLUGIN_VERSION" -> BuildInfo.version),
          stdout = ProcessOutput.Readlines(v => println(v)),
          mergeErrIntoOut = true,
          timeoutGracePeriod = 10000
        )
      }
    })
    runThread.start()

    val greet = runUntil("http://localhost:9000/greet", 200, "Hello World")
    tester.modifyFile(tester.workspacePath / "app" / "src" / "App.scala", _ => os.read(resourceDir / "changes" / "app" / "src" / "App.scala.1"))
    val greetReloaded = runUntil("http://localhost:9000/greet_reloaded", 200, "World Hello")

    tester.close()

    assert(greet && greetReloaded)
  }

  test("zio-http-multiproject") {
    val resourceDir = os.Path(BuildInfo.resourceDir) / "zio-http-multiproject"

    val tester = new IntegrationTester(
      daemonMode = false,
      workspaceSourcePath = resourceDir,
      millExecutable = os.Path(BuildInfo.exePath),
      // debugLog = true
    )

    val runThread = new Thread(new Runnable() {
      override def run(): Unit = {
        tester.eval(
          "project-a.liveReloadRun",
          env = Map("PLUGIN_VERSION" -> BuildInfo.version),
          stdout = ProcessOutput.Readlines(v => println(v)),
          mergeErrIntoOut = true
        )
      }
    })
    runThread.start()

    val greet = runUntil("http://localhost:9000/greet", 200, "Hello World")
    tester.modifyFile(tester.workspacePath / "project-a" / "src" / "App.scala", _ => os.read(resourceDir / "changes" / "project-a" / "src" / "App.scala.1"))
    tester.modifyFile(tester.workspacePath / "project-b" / "src" / "Text.scala", _ => os.read(resourceDir / "changes" / "project-b" / "src" / "Text.scala.1"))
    val greetReloaded = runUntil("http://localhost:9000/greet_reloaded", 200, "World Hello!")

    tester.close()

    assert(greet && greetReloaded)
  }

}
