package me.seroperson.reload.live.sbt

import java.util.function.Supplier
import me.seroperson.reload.live.hook.Hook
import me.seroperson.reload.live.runner.CompileResult
import me.seroperson.reload.live.runner.DevServerRunner
import play.dev.filewatch.FileWatchService
import play.dev.filewatch.LoggerProxy
import sbt._
import sbt.internal.inc.Analysis
import sbt.plugins.JvmPlugin

object LiveReloadPlugin extends AutoPlugin {

  val autoImport = LiveKeys

  import autoImport._
  import sbt.Keys._

  override def trigger = noTrigger

  override def requires = JvmPlugin

  override lazy val globalSettings = Seq()

  override lazy val projectSettings = Seq(
    libraryDependencies ++= Seq(
      "me.seroperson" % "jvm-live-reload-webserver" % "0.0.1",
      "me.seroperson" %% "jvm-live-reload-hooks" % "0.0.1"
    ),
    liveFileWatchService := {
      FileWatchService.defaultWatchService(
        target.value,
        pollInterval.value.toMillis.toInt,
        null.asInstanceOf[LoggerProxy]
      )
    },
    liveInteractionMode := ConsoleInteractionMode,
    liveAssetsClassLoader := { (parent: ClassLoader) => parent },
    liveDevSettings := Nil,
    liveMonitoredFiles := Commands.liveMonitoredFilesTask.value,
    // all dependencies from outside the project (all dependency jars)
    liveDependencyClasspath := (Runtime / externalDependencyClasspath).value,
    // all user classes, in this project and any other subprojects that it depends on
    liveReloaderClasspath := Classpaths
      .concatDistinct(
        Runtime / exportedProducts,
        Runtime / internalDependencyClasspath
      )
      .value,
    // filter out asset directories from the classpath (supports sbt-web 1.0 and 1.1)
    liveReloaderClasspath ~= {
      _.filter(_ => true) // .filter(_.get(WebKeys.webModulesLib.key).isEmpty)
    },
    liveCommonClassloader := Commands.liveCommonClassloaderTask.value,
    liveReload := Commands.liveReloadTask.value,
    liveCompileEverything := Commands.liveCompileEverythingTask.value
      .asInstanceOf[Seq[Analysis]],
    liveStartupHooks := Seq(
      HookIoAppStartup,
      HookRestApiHealthCheckStartup
      // HookTcpPortHealthCheckStartup
    ),
    liveShutdownHooks := Seq(
      HookIoAppShutdown,
      HookZioAppShutdown,
      HookCaskShutdown,
      HookRestApiHealthCheckShutdown
      // HookTcpPortHealthCheckShutdown
    ),
    Compile / bgRun := Commands.liveBgRunTask.evaluated,
    Compile / run := Commands.liveDefaultRunTask.evaluated,
    Compile / run / mainClass := Some(
      "me.seroperson.reload.live.webserver.DevServerStart"
    )
  )
}
