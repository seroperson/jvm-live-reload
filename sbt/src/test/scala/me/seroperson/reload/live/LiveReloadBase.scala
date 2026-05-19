package me.seroperson.reload.live

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import me.seroperson.reload.live.sbt.BuildInfo
import me.seroperson.sbt.testkit.*
import org.scalatest.funsuite.AnyFunSuite
import scala.util.Failure
import scala.util.Success
import scala.util.Try

trait LiveReloadBase extends AnyFunSuite {

  /** Sbt versions every scenario is exercised against by default */
  protected val SupportedSbtVersions: Seq[String] =
    Seq("1.12.3", "2.0.0-RC10")

  /** Hard cap on a single verify-after-reload poll loop. */
  protected val ReloadTimeoutMillis = 60_000L
  protected val RetryInterval = 1000L // ms

  /** Registers `body` as one ScalaTest case per requested sbt version */
  protected def testEach(
      name: String,
      versions: Seq[String] = SupportedSbtVersions
  )(body: String => Unit): Unit =
    versions.foreach { sbtVersion =>
      test(s"$name [sbt=$sbtVersion]")(body(sbtVersion))
    }

  /** Polls `attempt` until it succeeds or the deadline elapses. */
  protected def pollUntil(label: String)(attempt: => Unit): Unit = {
    val deadline = System.currentTimeMillis() + ReloadTimeoutMillis
    var lastError: Option[Throwable] = None
    while (System.currentTimeMillis() < deadline) {
      Try(attempt) match {
        case Success(_)  => return
        case Failure(ex) =>
          lastError = Some(ex)
          Thread.sleep(RetryInterval)
      }
    }
    throw new AssertionError(
      s"$label timed out after ${ReloadTimeoutMillis}ms: ${lastError.map(_.getMessage).getOrElse("unknown")}",
      lastError.orNull
    )
  }

  private val portCounter = new java.util.concurrent.atomic.AtomicInteger(19000)

  protected val httpClient: HttpClient = HttpClient
    .newBuilder()
    .connectTimeout(Duration.ofSeconds(5))
    .build()

  /** Allocates a unique (proxyPort, appPort) pair for each test */
  protected def nextPortPair(): (Int, Int) = {
    val proxy = portCounter.getAndAdd(2)
    (proxy, proxy + 1)
  }

  protected def withRunner(
      resourceDir: String,
      sbtVersion: String
  )(body: (SbtRunner, Int) => Unit): Unit = {
    val (proxyPort, appPort) = nextPortPair()
    val runner = SbtRunner
      .inTemp()
      .withDirectoryFromResources(resourceDir)
      .withSbtVersion(sbtVersion)
      .withJvmOptions(
        s"-Dproject.version=${BuildInfo.version}",
        s"-Dtestkit.proxyPort=$proxyPort",
        s"-Dtestkit.port=$appPort"
      )
      .withAttachedStdio()
      .withDebugLogging()
      .build()
    try body(runner, proxyPort)
    finally runner.close()
  }

  /** Polls until a TCP connect to `port` is refused, ie. nothing is listening
    */
  protected def verifyPortClosed(port: Int): Unit = {
    pollUntil(s"TCP connect to $port should be refused") {
      val sock = new Socket()
      try {
        sock.connect(new InetSocketAddress("localhost", port), 1000)
        throw new AssertionError(s"Port $port is still accepting connections")
      } catch {
        case _: IOException => ()
      } finally sock.close()
    }
  }

  protected def verifyHttp(
      path: String,
      expectedStatus: Int,
      expectedBody: Option[String] = None,
      port: Int
  ): Unit = {
    val request = HttpRequest
      .newBuilder()
      .uri(URI.create(s"http://localhost:$port/$path"))
      .timeout(Duration.ofSeconds(5))
      .GET()
      .build()

    pollUntil(s"HTTP /$path (status=$expectedStatus, body=$expectedBody)") {
      val response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      assert(
        response.statusCode() == expectedStatus,
        s"Expected status $expectedStatus for /$path, got ${response.statusCode()}"
      )
      expectedBody.foreach { body =>
        val actualBody = response.body()
        assert(
          actualBody == body,
          s"Expected body '$body' for /$path, got '$actualBody'"
        )
      }
    }
  }
}
