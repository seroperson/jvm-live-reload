package me.seroperson.reload.live.mill.test

import mill.testkit.IntegrationTester
import org.scalatest.funsuite.AnyFunSuite
import os.ProcessOutput

class IntegrationTests extends AnyFunSuite with RequestMaker {

  def hexStringToBytes(hex: String): Array[Byte] = {
    new java.math.BigInteger(hex, 16).toByteArray
  }

  /*test("zio-http") {
    val resourceDir = os.Path(BuildInfo.resourceDir) / "zio-http"

    val tester = new IntegrationTester(
      daemonMode = false,
      workspaceSourcePath = resourceDir,
      millExecutable = os.Path(BuildInfo.exePath)
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
    tester.modifyFile(
      tester.workspacePath / "app" / "src" / "App.scala",
      _ => os.read(resourceDir / "changes" / "app" / "src" / "App.scala.1")
    )
    val greetReloaded =
      runUntil("http://localhost:9000/greet_reloaded", 200, "World Hello")

    tester.close()

    assert(greet && greetReloaded)
  }

  test("zio-http-propagate-env") {
    val resourceDir = os.Path(BuildInfo.resourceDir) / "zio-http-propagate-env"

    val tester = new IntegrationTester(
      daemonMode = false,
      workspaceSourcePath = resourceDir,
      millExecutable = os.Path(BuildInfo.exePath)
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
    tester.modifyFile(
      tester.workspacePath / "app" / "src" / "App.scala",
      _ => os.read(resourceDir / "changes" / "app" / "src" / "App.scala.1")
    )
    val greetReloaded =
      runUntil("http://localhost:9000/greet_reloaded", 200, "World Hello")

    tester.close()

    assert(greet && greetReloaded)
  }*/

  /*test("zio-http-multiproject") {
    val resourceDir = os.Path(BuildInfo.resourceDir) / "zio-http-multiproject"

    val tester = new IntegrationTester(
      daemonMode = false,
      workspaceSourcePath = resourceDir,
      millExecutable = os.Path(BuildInfo.exePath)
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
    tester.modifyFile(
      tester.workspacePath / "project-a" / "src" / "App.scala",
      _ =>
        os.read(resourceDir / "changes" / "project-a" / "src" / "App.scala.1")
    )
    tester.modifyFile(
      tester.workspacePath / "project-b" / "src" / "Text.scala",
      _ =>
        os.read(resourceDir / "changes" / "project-b" / "src" / "Text.scala.1")
    )
    val greetReloaded =
      runUntil("http://localhost:9000/greet_reloaded", 200, "World Hello!")

    tester.close()

    assert(greet && greetReloaded)
  }*/

  test("http4s-with-resources") {
    val resourceDir = os.Path(BuildInfo.resourceDir) / "http4s-with-resources"

    val tester = new IntegrationTester(
      daemonMode = false,
      workspaceSourcePath = resourceDir,
      millExecutable = os.Path(BuildInfo.exePath)
      // debugLog = true
    )

    var process = tester
      .spawn(
        cmd = "app.liveReloadRun",
        env = Map("PLUGIN_VERSION" -> BuildInfo.version),
        stdout = ProcessOutput.Readlines(v => println(v)),
        stderr = os.Pipe,
        mergeErrIntoOut = true
      )
      .process

    val greet = runUntil("http://localhost:9000/greet", 200, "Hello World 1")
    tester.modifyFile(
      tester.workspacePath / "app" / "resources" / "application.conf",
      _ => os.read(resourceDir / "changes" / "application.conf.1")
    )
    val greetResourceReloaded =
      runUntil("http://localhost:9000/greet", 200, "Hello World 2")
    tester.modifyFile(
      tester.workspacePath / "app" / "src" / "App.scala",
      _ => os.read(resourceDir / "changes" / "App.scala.1")
    )
    val greetReloaded =
      runUntil("http://localhost:9000/greet_reloaded", 200, "World Hello 2")

    process.stdin.write(10) // write "Enter"
    process.stdin.flush()
    process.join()
    tester.close()

    assert(greet && greetResourceReloaded && greetReloaded)
  }

  test("grpc-scalapb") {
    val resourceDir = os.Path(BuildInfo.resourceDir) / "grpc-scalapb"

    val tester = new IntegrationTester(
      daemonMode = false,
      workspaceSourcePath = resourceDir,
      millExecutable = os.Path(BuildInfo.exePath)
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

    val greet = runGrpcUntil(
      "localhost",
      9000, // Proxy port
      "Greeter",
      "SayHello",
      // Hello World!
      hexStringToBytes("0a0c48656c6c6f20576f726c6421"),
      // World Hello!
      hexStringToBytes("0a0c576f726c642048656c6c6f21")
    )
    tester.modifyFile(
      tester.workspacePath / "app" / "src" / "App.scala",
      _ => os.read(resourceDir / "changes" / "app" / "src" / "App.scala.1")
    )
    val greetReloaded = runGrpcUntil(
      "localhost",
      9000, // Proxy port
      "Greeter",
      "SayHello",
      hexStringToBytes("0a0c48656c6c6f20576f726c6421"),
      // Hello World Reloaded!
      hexStringToBytes("0a1548656c6c6f20576f726c642052656c6f6164656421")
    )

    tester.close()

    assert(greet && greetReloaded)
  }

  test("grpc-streaming") {
    val resourceDir = os.Path(BuildInfo.resourceDir) / "grpc-streaming"

    val tester = new IntegrationTester(
      daemonMode = false,
      workspaceSourcePath = resourceDir,
      millExecutable = os.Path(BuildInfo.exePath)
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

    val initial = runServerStreamingUntil(
      "localhost",
      9000,
      "greeter.Greeter",
      "StreamGreet",
      Array.emptyByteArray,
      Seq(
        "Hi-1".getBytes("UTF-8"),
        "Hi-2".getBytes("UTF-8"),
        "Hi-3".getBytes("UTF-8")
      )
    )
    tester.modifyFile(
      tester.workspacePath / "app" / "src" / "App.scala",
      _ => os.read(resourceDir / "changes" / "app" / "src" / "App.scala.1")
    )
    val reloaded = runServerStreamingUntil(
      "localhost",
      9000,
      "greeter.Greeter",
      "StreamGreet",
      Array.emptyByteArray,
      Seq(
        "Yo-1".getBytes("UTF-8"),
        "Yo-2".getBytes("UTF-8"),
        "Yo-3".getBytes("UTF-8")
      )
    )

    tester.close()

    assert(initial && reloaded)
  }

  test("grpc-multiproject") {
    val resourceDir = os.Path(BuildInfo.resourceDir) / "grpc-multiproject"

    val tester = new IntegrationTester(
      daemonMode = false,
      workspaceSourcePath = resourceDir,
      millExecutable = os.Path(BuildInfo.exePath)
    )

    val runThread = new Thread(new Runnable() {
      override def run(): Unit = {
        tester.eval(
          "project-a.liveReloadRun",
          env = Map("PLUGIN_VERSION" -> BuildInfo.version),
          stdout = ProcessOutput.Readlines(v => println(v)),
          mergeErrIntoOut = true,
          timeoutGracePeriod = 10000
        )
      }
    })
    runThread.start()

    val initial = runGrpcUntil(
      "localhost",
      9000,
      "greeter.Greeter",
      "Greet",
      Array.emptyByteArray,
      "Multi-Hi".getBytes("UTF-8")
    )
    tester.modifyFile(
      tester.workspacePath / "project-b" / "src" / "Greeting.scala",
      _ =>
        os.read(
          resourceDir / "changes" / "project-b" / "src" / "Greeting.scala.1"
        )
    )
    val reloaded = runGrpcUntil(
      "localhost",
      9000,
      "greeter.Greeter",
      "Greet",
      Array.emptyByteArray,
      "Multi-Yo".getBytes("UTF-8")
    )

    tester.close()

    assert(initial && reloaded)
  }

  test("grpc-scala3") {
    val resourceDir = os.Path(BuildInfo.resourceDir) / "grpc-scala3"

    val tester = new IntegrationTester(
      daemonMode = false,
      workspaceSourcePath = resourceDir,
      millExecutable = os.Path(BuildInfo.exePath)
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

    val initial = runGrpcUntil(
      "localhost",
      9000,
      "greeter.Greeter",
      "Greet",
      Array.emptyByteArray,
      "Scala3-Hi".getBytes("UTF-8")
    )
    tester.modifyFile(
      tester.workspacePath / "app" / "src" / "App.scala",
      _ => os.read(resourceDir / "changes" / "app" / "src" / "App.scala.1")
    )
    val reloaded = runGrpcUntil(
      "localhost",
      9000,
      "greeter.Greeter",
      "Greet",
      Array.emptyByteArray,
      "Scala3-Yo".getBytes("UTF-8")
    )

    tester.close()

    assert(initial && reloaded)
  }

  test("grpc-reflection") {
    val resourceDir = os.Path(BuildInfo.resourceDir) / "grpc-streaming"

    val tester = new IntegrationTester(
      daemonMode = false,
      workspaceSourcePath = resourceDir,
      millExecutable = os.Path(BuildInfo.exePath)
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

    val ok =
      runReflectionListServicesUntil("localhost", 9000, "grpc.health.v1.Health")

    tester.close()

    assert(ok)
  }

  test("grpc-tls") {
    val resourceDir = os.Path(BuildInfo.resourceDir) / "grpc-tls"

    val tester = new IntegrationTester(
      daemonMode = false,
      workspaceSourcePath = resourceDir,
      millExecutable = os.Path(BuildInfo.exePath)
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

    val trust = (tester.workspacePath / "cert.pem").toIO
    val initial = runGrpcTlsUntil(
      "localhost",
      9000,
      "greeter.Greeter",
      "Greet",
      Array.emptyByteArray,
      "Secure-Hi".getBytes("UTF-8"),
      trust
    )
    tester.modifyFile(
      tester.workspacePath / "app" / "src" / "App.scala",
      _ => os.read(resourceDir / "changes" / "app" / "src" / "App.scala.1")
    )
    val reloaded = runGrpcTlsUntil(
      "localhost",
      9000,
      "greeter.Greeter",
      "Greet",
      Array.emptyByteArray,
      "Secure-Yo".getBytes("UTF-8"),
      trust
    )

    tester.close()

    assert(initial && reloaded)
  }

}
