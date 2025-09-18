/*import java.nio.file.Files
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import sbt._
import sbt.Keys._
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.sys.process.Process

def check(
    log: sbt.internal.util.ManagedLogger,
    port: Int,
    path: String,
    expectedStatus: Int,
    expectedBody: Option[String]
): Unit = {

  import java.net.HttpURLConnection
  import java.net.URI
  import java.net.URL
  import javax.net.ssl.HostnameVerifier
  import javax.net.ssl.HttpsURLConnection
  import javax.net.ssl.SSLSession
  import scala.io.Source

  HttpsURLConnection.setDefaultHostnameVerifier(
    new HostnameVerifier() {
      override def verify(
          hostname: String,
          sslSession: SSLSession
      ): Boolean = {
        hostname == "localhost"
      }
    }
  )

  val url: String = s"http://localhost:${port}${path}"

  val c: HttpURLConnection =
    (new URI(url)
      .toURL())
      .openConnection
      .asInstanceOf[HttpURLConnection]

  val name: String = s"GET ${url}"

  c.setInstanceFollowRedirects(false)
  c.setRequestMethod("GET")
  c.setDoOutput(false)

  println("Before get response code")
  val obtainedStatus: Int =
    c.getResponseCode()
  println(s"After get response code: ${obtainedStatus}")

  val obtainedBody: String =
    try {
      Source.fromInputStream(c.getInputStream()).mkString
    } catch {
      case e: Exception =>
        try {
          e.printStackTrace()
          Source.fromInputStream(c.getErrorStream()).mkString
        } catch {
          case x: Throwable =>
            x.printStackTrace()
            ""
        }
      case t @ _ =>
        println("Error body " + t)
        t.printStackTrace()
        ""
    } finally {
      println("Finally in body reading")
    }

  val statusMatch: Boolean =
    expectedStatus == obtainedStatus

  val bodyMatch: Boolean = expectedBody match {
    case Some(body) =>
      body == obtainedBody
    case None =>
      obtainedBody.isEmpty
  }

  if (!statusMatch && !bodyMatch) {
    sys.error(
      s"""|${name}:
          |  expected:
          |    * status: ${expectedStatus}
          |    * body:
          |      > ${expectedBody
           .toString()
           .replaceAll("\n", "\n      > ")}
          |  obtained:
          |    * status: ${obtainedStatus}
          |    * body:
          |      > ${obtainedBody
           .toString()
           .replaceAll("\n", "\n      > ")}""".stripMargin
    )
  } else if (!statusMatch) {
    sys.error(
      s"""|${name}:
          |  expected status: ${expectedStatus}
          |  obtained status: ${obtainedStatus}""".stripMargin
    )
  } else if (!bodyMatch) {
    sys.error(
      s"""|${name}:
          |  expected body:
          |      > ${expectedBody
           .toString()
           .replaceAll("\n", "\n      > ")}
          |  obtained body:
          |      > ${obtainedBody
           .toString()
           .replaceAll("\n", "\n      > ")}""".stripMargin
    )
  } else {
    log.success(
      s"""|${name}:
          |  expected:
          |    * status: ${expectedStatus}
          |    * body:
          |      > ${expectedBody
           .toString()
           .replaceAll("\n", "\n      > ")}
          |  obtained:
          |    * status: ${obtainedStatus}
          |    * body:
          |      > ${obtainedBody
           .toString()
           .replaceAll("\n", "\n      > ")}""".stripMargin
    )
  }

}

lazy val root = (project in file("."))
  .enablePlugins(LiveReloadPlugin)
  .settings(
    resolvers += Resolver.mavenLocal,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-http" % "3.3.3",
      "org.slf4j" % "slf4j-simple" % "2.0.16"
    ),
    InputKey[Unit]("verifyResourceContains") := {
      val args = Def.spaceDelimited("<path> <status> <words> ...").parsed
      val path :: status :: assertions = args

      check(streams.value.log, 9000, path, status.toInt, assertions.headOption)
    }
  )*/

lazy val root = (project in file("."))
  .enablePlugins(LiveReloadPlugin)
  .settings(
    resolvers += Resolver.mavenLocal,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-http" % "3.3.3",
      "org.slf4j" % "slf4j-simple" % "2.0.16"
    ),
    InputKey[Unit]("verifyResourceContains") := {
      val args = Def.spaceDelimited("<path> <status> <words> ...").parsed
      val path :: status :: assertions = args
      ScriptedTools.verifyResourceContains(path, status.toInt, assertions)
    }
  )
