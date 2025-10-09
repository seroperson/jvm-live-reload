package me.seroperson.reload.live.sbt

import java.io.File
import java.net.URL
import play.dev.filewatch.FileWatchService
import sbt.Keys.Classpath
import sbt.internal.inc.Analysis
import sbt.settingKey
import sbt.taskKey

object LiveKeys {

  object HookClassnames {
    // format: off
    val IoAppStartup = "me.seroperson.reload.live.hook.io.IoAppStartupHook"
    val ZioAppStartup = "me.seroperson.reload.live.hook.zio.ZioAppStartupHook"
    val RestApiHealthCheckStartup = "me.seroperson.reload.live.hook.RestApiHealthCheckStartupHook"

    val IoAppShutdown = "me.seroperson.reload.live.hook.io.IoAppShutdownHook"
    val ZioAppShutdown = "me.seroperson.reload.live.hook.zio.ZioAppShutdownHook"
    val RuntimeShutdown = "me.seroperson.reload.live.hook.RuntimeShutdownHook"
    val RestApiHealthCheckShutdown = "me.seroperson.reload.live.hook.RestApiHealthCheckShutdownHook"
    val ThreadInterruptShutdown = "me.seroperson.reload.live.hook.ThreadInterruptShutdownHook"
    // format: on
  }

  val liveFileWatchService =
    settingKey[FileWatchService]("The watch service to catch file changes.")

  val liveInteractionMode = settingKey[InteractionMode](
    "Console interaction mode (non-interactive or interactive)."
  )

  val liveHookBundle = taskKey[Option[HookBundle]](
    "If defined, hooks are loaded from predefined set."
  )
  val liveStartupHooks =
    taskKey[Seq[String]]("The list of startup hooks (classnames).")
  val liveShutdownHooks =
    taskKey[Seq[String]]("The list of shutdown hooks (classnames).")

  val liveDevSettings =
    settingKey[Seq[(String, String)]]("Development server settings.")

  val liveMonitoredFiles =
    taskKey[Seq[File]]("The list of files to be monitored for changes.")

  val liveDependencyClasspath = taskKey[Classpath](
    "The classpath containing all the jar dependencies of the project."
  )
  val liveReloaderClasspath = taskKey[Classpath](
    "The application classpath, containing all projects in this build that are dependencies of this project, including this project."
  )

  val liveReload = taskKey[Analysis](
    "Executed when sources of changed, to recompile (and possibly reload) the app."
  )
  val liveCompileEverything = taskKey[Seq[Analysis]](
    "Compiles this project and every project it depends on."
  )
}
