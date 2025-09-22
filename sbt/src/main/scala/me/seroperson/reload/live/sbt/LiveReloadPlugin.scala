package me.seroperson.reload.live.sbt

import java.util.function.Supplier
import me.seroperson.reload.live.hook.Hook
import me.seroperson.reload.live.runner.CompileResult
import me.seroperson.reload.live.runner.DevServerRunner
import play.dev.filewatch.FileWatchService
import play.dev.filewatch.LoggerProxy
import sbt.*
import sbt.internal.inc.Analysis
import sbt.plugins.JvmPlugin

/** SBT plugin that provides live reload functionality for JVM applications.
  *
  * This plugin enables automatic recompilation and application restart when
  * source code changes are detected. It works by:
  *
  *   1. Setting up a proxy server that forwards requests to the application
  *   2. Monitoring source files for changes
  *   3. Recompiling and reloading the application when changes are detected
  *   4. Managing application lifecycle through configurable hooks
  *
  * The plugin supports various frameworks through specific hooks (Cats Effect,
  * ZIO, Cask) and provides both blocking and non-blocking interaction modes.
  */
object LiveReloadPlugin extends AutoPlugin {

  val autoImport = LiveKeys

  import autoImport.*
  import sbt.Keys.*

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
    liveAssetsClassLoader := SbtCompat.uncached { (parent: ClassLoader) =>
      parent
    },
    liveDevSettings := Nil,
    liveMonitoredFiles := Commands.liveMonitoredFilesTask.value,
    // all dependencies from outside the project (all dependency jars)
    liveDependencyClasspath := SbtCompat.uncached(
      (Runtime / externalDependencyClasspath).value
    ),
    // all user classes, in this project and any other subprojects that it depends on
    liveReloaderClasspath := SbtCompat.uncached(
      Classpaths
        .concatDistinct(
          Runtime / exportedProducts,
          Runtime / internalDependencyClasspath
        )
        .value
    ),
    // filter out asset directories from the classpath (supports sbt-web 1.0 and 1.1)
    liveReloaderClasspath ~= SbtCompat.uncached {
      _.filter(_ => true) // .filter(_.get(WebKeys.webModulesLib.key).isEmpty)
    },
    liveCommonClassloader := SbtCompat.uncached(
      Commands.liveCommonClassloaderTask.value
    ),
    liveReload := SbtCompat.uncached(Commands.liveReloadTask.value),
    liveCompileEverything := SbtCompat.uncached(
      Commands.liveCompileEverythingTask.value
        .asInstanceOf[Seq[Analysis]]
    ),
    liveStartupHooks := Seq(
      HookIoAppStartup,
      HookRestApiHealthCheckStartup
    ),
    liveShutdownHooks := Seq(
      HookIoAppShutdown,
      HookZioAppShutdown,
      HookCaskShutdown,
      HookRestApiHealthCheckShutdown
    ),
    Compile / bgRun := Commands.liveBgRunTask.evaluated,
    Compile / run := Commands.liveDefaultRunTask.map(_ => ()).evaluated,
    Compile / run / mainClass := Some(
      "me.seroperson.reload.live.webserver.DevServerStart"
    )
  )
}
