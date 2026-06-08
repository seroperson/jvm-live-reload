val Http4sVersion = "0.23.30"

enablePlugins(LiveReloadPlugin)
enablePlugins(BuildInfoPlugin)

scalaVersion := "2.13.16"
resolvers += Resolver.mavenLocal
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-server" % Http4sVersion,
  "org.http4s" %% "http4s-dsl" % Http4sVersion,
  "org.typelevel" %% "cats-effect" % "3.6.3"
)

val isSbt2 = settingKey[Boolean]("isSbt2")
isSbt2 := (sbtBinaryVersion.value match {
  case "2" => true
  case _   => false
})

val proxyPort = settingKey[Int]("proxyPort")
proxyPort := sys.props.get("testkit.proxyPort").map(_.toInt).getOrElse(if (isSbt2.value) 9001 else 9000)

val port = settingKey[Int]("port")
port := sys.props.get("testkit.port").map(_.toInt).getOrElse(if (isSbt2.value) 8081 else 8080)

liveDevSettings := Seq(
  DevSettingsKeys.LiveReloadProxyHttpPort -> proxyPort.value.toString,
  DevSettingsKeys.LiveReloadHttpPort -> port.value.toString,
  DevSettingsKeys.LiveReloadIsDebug -> "true"
)

livePropagateEnv := Map(
  "JLR_LEAK_CHECK" -> "leaked"
)

// Dumps the live state of the sbt JVM's own process environment. After a
// failed bgRun, the test runs this and asserts JLR_LEAK_CHECK is absent,
// proving the propagated env was actually rolled back (not just that a log
// line was printed). System.getenv reads the same ProcessEnvironment map
// that Environment.putEnv/setEnv mutate, so the leak is observable here.
val dumpLeakCheckEnv =
  taskKey[Unit]("Logs whether JLR_LEAK_CHECK is present in the sbt JVM environment")
dumpLeakCheckEnv := {
  val value = Option(System.getenv("JLR_LEAK_CHECK")).getOrElse("<absent>")
  streams.value.log.info(s"JLR_LEAK_CHECK_ENV=$value")
}

buildInfoKeys := Seq[BuildInfoKey](port)
buildInfoPackage := "me.seroperson"
