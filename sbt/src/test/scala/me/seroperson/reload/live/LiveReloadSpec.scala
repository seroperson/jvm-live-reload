package me.seroperson.reload.live

import java.net.InetSocketAddress
import java.net.ServerSocket

class LiveReloadSpec extends LiveReloadBase {

  testEach("http4s-slow-start - concurrent first requests wait for startup") {
    sbtVersion =>
      withRunner("http4s-slow-start", sbtVersion) { (runner, proxyPort) =>
        runner.run("bgRun")
        verifyHttpConcurrent(
          Seq("greet", "greet2"),
          200,
          Some("hello"),
          proxyPort
        )
      }
  }

  testEach("http4s - live reload on source change") { sbtVersion =>
    withRunner("http4s", sbtVersion) { (runner, proxyPort) =>
      runner.run("bgRun")
      verifyHttp("greet", 200, Some("Hello World"), proxyPort)
      runner.copyFile("changes/App.scala.1", "src/main/scala/App.scala")
      verifyHttp("greet_reloaded", 200, Some("World Hello"), proxyPort)
      verifyHttp("greet", 404, Some("Not found"), proxyPort)
    }
  }

  testEach("zio-http - live reload on source change") { sbtVersion =>
    withRunner("zio-http", sbtVersion) { (runner, proxyPort) =>
      runner.run("bgRun")
      verifyHttp("greet", 200, Some("Hello World"), proxyPort)
      runner.copyFile("changes/App.scala.1", "src/main/scala/App.scala")
      verifyHttp("greet_reloaded", 200, Some("World Hello"), proxyPort)
      verifyHttp("greet", 404, port = proxyPort)
    }
  }

  testEach("cask - live reload on source change") { sbtVersion =>
    withRunner("cask", sbtVersion) { (runner, proxyPort) =>
      runner.run("bgRun")
      verifyHttp("greet", 200, Some("Hello World"), proxyPort)
      runner.copyFile("changes/App.scala.1", "src/main/scala/App.scala")
      verifyHttp("greet_reloaded", 200, Some("World Hello"), proxyPort)
      verifyHttp("greet", 404, Some("Error 404: Not Found"), proxyPort)
    }
  }

  testEach("startup crash before bind returns HTTP 503") { sbtVersion =>
    withRunner("startup-crash", sbtVersion) { (runner, proxyPort) =>
      runner.run("bgRun")
      verifyHttp("greet", 503, Some("dev server stopped"), proxyPort)
      verifyPortClosed(proxyPort)
    }
  }

  testEach(
    "cask - hung main thread triggers unrecoverable shutdown",
    Seq("2.0.0-RC10")
  ) { sbtVersion =>
    withRunner("cask-hang", sbtVersion) { (runner, proxyPort) =>
      runner.run("bgRun")
      verifyHttp("greet", 200, Some("Hello World"), proxyPort)
      runner.copyFile("changes/App.scala.1", "src/main/scala/App.scala")
      verifyHttp("greet", 503, Some("dev server stopped"), proxyPort)
      verifyPortClosed(proxyPort)
    }
  }

  testEach("http4s - add new file triggers reload") { sbtVersion =>
    withRunner("http4s-add-new-file", sbtVersion) { (runner, proxyPort) =>
      runner.run("bgRun")
      verifyHttp("greet", 200, Some("Hello World"), proxyPort)
      runner.delete("src/main/scala/App.scala")
      runner.copyFile("changes/NewApp.scala.1", "src/main/scala/NewApp.scala")
      runner.copyFile(
        "changes/NewClass.scala.1",
        "src/main/scala/NewClass.scala"
      )
      verifyHttp("greet_reloaded", 200, Some("World Hello 1"), proxyPort)
      verifyHttp("greet", 404, Some("Not found"), proxyPort)
    }
  }

  testEach("http4s - dotenv environment variables") { sbtVersion =>
    withRunner("http4s-dotenv", sbtVersion) { (runner, proxyPort) =>
      runner.run("bgRun")
      verifyHttp("greet", 200, Some("Hello World"), proxyPort)
      runner.copyFile("changes/App.scala.1", "src/main/scala/App.scala")
      verifyHttp("greet_reloaded", 200, Some("World Hello"), proxyPort)
      verifyHttp("greet", 404, Some("Not found"), proxyPort)
    }
  }

  testEach("http4s - propagate-env environment variables") { sbtVersion =>
    withRunner("http4s-propagate-env", sbtVersion) { (runner, proxyPort) =>
      runner.run("bgRun")
      verifyHttp("greet", 200, Some("Hello World"), proxyPort)
      runner.copyFile("changes/App.scala.1", "src/main/scala/App.scala")
      verifyHttp("greet_reloaded", 200, Some("World Hello"), proxyPort)
      verifyHttp("greet", 404, Some("Not found"), proxyPort)
    }
  }

  testEach("http4s - reload with resource files") { sbtVersion =>
    withRunner("http4s-with-resources", sbtVersion) { (runner, proxyPort) =>
      runner.run("bgRun")
      verifyHttp("greet", 200, Some("Hello World 1"), proxyPort)
      runner.copyFile("changes/App.scala.1", "src/main/scala/App.scala")
      runner.copyFile(
        "changes/application.conf.1",
        "src/main/resources/application.conf"
      )
      verifyHttp("greet_reloaded", 200, Some("World Hello 2"), proxyPort)
      verifyHttp("greet", 404, Some("Not found"), proxyPort)
    }
  }

  testEach("zio-http - reload with resource files") { sbtVersion =>
    withRunner("zio-http-with-resources", sbtVersion) { (runner, proxyPort) =>
      runner.run("bgRun")
      verifyHttp("greet", 200, Some("Hello World 1"), proxyPort)
      runner.copyFile("changes/App.scala.1", "src/main/scala/App.scala")
      runner.copyFile(
        "changes/application.conf.1",
        "src/main/resources/application.conf"
      )
      verifyHttp("greet_reloaded", 200, Some("World Hello 2"), proxyPort)
      verifyHttp("greet", 404, port = proxyPort)
    }
  }

  testEach("zio-http - multi-project reload") { sbtVersion =>
    withRunner("zio-http-multiproject", sbtVersion) { (runner, proxyPort) =>
      runner.run("project-a/bgRun")
      verifyHttp("greet", 200, Some("Hello World"), proxyPort)
      runner.copyFile(
        "changes/App.scala.1",
        "project-a/src/main/scala/App.scala"
      )
      runner.copyFile(
        "changes/Text.scala.1",
        "project-b/src/main/scala/Text.scala"
      )
      verifyHttp("greet_reloaded", 200, Some("World Hello!"), proxyPort)
      verifyHttp("greet", 404, port = proxyPort)
    }
  }

  testEach(
    "http4s - throwing startup hook triggers unrecoverable shutdown",
    Seq("2.0.0-RC10")
  ) { sbtVersion =>
    withRunner("http4s", sbtVersion) { (runner, proxyPort) =>
      runner.run("bgRun")
      verifyHttp("greet", 200, Some("Hello World"), proxyPort)
      runner.copyFile(
        "changes/AppBrokenHealth.scala.1",
        "src/main/scala/App.scala"
      )
      verifyHttp("greet", 503, Some("dev server stopped"), proxyPort)
      verifyPortClosed(proxyPort)
    }
  }

  testEach(
    "http4s - propagated env is rolled back when proxy fails to bind",
    Seq("2.0.0-RC10")
  ) { sbtVersion =>
    withRunner("http4s-propagate-env-rollback", sbtVersion) {
      (runner, proxyPort) =>
        // Hold the proxy port from the test JVM so the sbt JVM's
        // proxy server.start() throws BindException. runBackground's
        // catch must call wrapper.close() to restore the env table
        // and shutdown hooks in the sbt JVM.
        val blocker = new ServerSocket()
        blocker.setReuseAddress(true)
        try {
          blocker.bind(new InetSocketAddress("localhost", proxyPort))
          val result = runner.run("bgRun")
          val logs = result.logs.mkString("\n")
          // The error log proves start() reached server.start() and threw,
          // i.e. it ran past Environment.putEnv - the env table was actually
          // mutated, so this is a real leak window, not a no-op.
          assert(
            result.logsContain("Error during proxy server initialization"),
            s"expected runBackground catch to fire, got logs:\n$logs"
          )
          // Directly observe the sbt JVM's own environment after the failed
          // start. dumpLeakCheckEnv reads System.getenv in the same JVM that
          // ran bgRun; if the rollback failed, JLR_LEAK_CHECK would still be
          // "leaked" here. Absence proves wrapper.close() restored the env.
          val dump = runner.run("dumpLeakCheckEnv")
          val dumpLogs = dump.logs.mkString("\n")
          assert(
            dump.logsContain("JLR_LEAK_CHECK_ENV=<absent>"),
            s"expected JLR_LEAK_CHECK to be rolled back out of the sbt env, got logs:\n$dumpLogs"
          )
        } finally blocker.close()
    }
  }
}
