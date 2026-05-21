package me.seroperson.reload.live

import java.net.InetSocketAddress
import java.net.ServerSocket

class LiveReloadSpec extends LiveReloadBase {

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
    "http4s - bgRun rolls back wrapper state when the proxy port is taken",
    Seq("2.0.0-RC10")
  ) { sbtVersion =>
    withRunner("http4s", sbtVersion) { (runner, proxyPort) =>
      // Occupy the proxy port from the test JVM so DevServerStart.start()
      // can't bind it. wrapper.start() throws, runBackground catches it,
      // and the wrapper's close() path restores host env vars + shutdown
      // hooks captured before the mutating sequence began.
      val blocker = new ServerSocket()
      blocker.setReuseAddress(true)
      blocker.bind(new InetSocketAddress("localhost", proxyPort))
      try {
        runner.run("bgRun")
        // bgRun is asynchronous; the inner task fails and the failure
        // must be surfaced rather than swallowed. The wrapper.close()
        // rollback can't be observed over HTTP (it touches the sbt JVM's
        // env table and shutdown-hook registry), so the assertion is
        // limited to "the proxy never claimed the port" — anything that
        // claimed and held the port after a failed bind would indicate
        // partial state in the wrapper that the rollback didn't undo.
      } finally blocker.close()
      verifyPortClosed(proxyPort)
    }
  }
}
