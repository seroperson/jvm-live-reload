package me.seroperson.reload.live.sbt

import java.io.File
import me.seroperson.reload.live.settings.DevServerSettings
import play.dev.filewatch.FileWatchService
import sbt.Keys.Classpath
import sbt.internal.inc.Analysis
import sbt.settingKey
import sbt.taskKey

sealed trait ServerType
case object HttpServerType extends ServerType
case object GrpcServerType extends ServerType

object LiveKeys {

  val LiveReloadPlugin = me.seroperson.reload.live.sbt.LiveReloadPlugin

  object HookClassnames {
    // format: off
    val IoAppStartup = "me.seroperson.reload.live.hook.io.IoAppStartupHook"
    val ZioAppStartup = "me.seroperson.reload.live.hook.zio.ZioAppStartupHook"
    val RestApiHealthCheckStartup = "me.seroperson.reload.live.hook.RestApiHealthCheckStartupHook"
    val GrpcHealthCheckStartup = "me.seroperson.reload.live.webserver.grpc.hook.GrpcHealthCheckStartupHook"

    val IoAppShutdown = "me.seroperson.reload.live.hook.io.IoAppShutdownHook"
    val ZioAppShutdown = "me.seroperson.reload.live.hook.zio.ZioAppShutdownHook"
    val RuntimeShutdown = "me.seroperson.reload.live.hook.RuntimeShutdownHook"
    val RestApiHealthCheckShutdown = "me.seroperson.reload.live.hook.RestApiHealthCheckShutdownHook"
    val GrpcHealthCheckShutdown = "me.seroperson.reload.live.webserver.grpc.hook.GrpcHealthCheckShutdownHook"
    val ThreadInterruptShutdown = "me.seroperson.reload.live.hook.ThreadInterruptShutdownHook"
    // format: on
  }

  object DevSettingsKeys {
    // format: off
    val LiveReloadProxyHttpHost: String = DevServerSettings.LiveReloadProxyHttpHost
    val LiveReloadProxyHttpPort: String = DevServerSettings.LiveReloadProxyHttpPort
    val LiveReloadHttpHost: String = DevServerSettings.LiveReloadHttpHost
    val LiveReloadHttpPort: String = DevServerSettings.LiveReloadHttpPort
    val LiveReloadHealthPath: String = DevServerSettings.LiveReloadHealthPath
    val LiveReloadProxyGrpcHost: String = DevServerSettings.LiveReloadProxyGrpcHost
    val LiveReloadProxyGrpcPort: String = DevServerSettings.LiveReloadProxyGrpcPort
    val LiveReloadGrpcHost: String = DevServerSettings.LiveReloadGrpcHost
    val LiveReloadGrpcPort: String = DevServerSettings.LiveReloadGrpcPort
    val LiveReloadGrpcHealthService: String = DevServerSettings.LiveReloadGrpcHealthService
    val LiveReloadGrpcTargetTls: String = DevServerSettings.LiveReloadGrpcTargetTls
    val LiveReloadGrpcTargetTlsTrust: String = DevServerSettings.LiveReloadGrpcTargetTlsTrust
    val LiveReloadGrpcProxyTlsCert: String = DevServerSettings.LiveReloadGrpcProxyTlsCert
    val LiveReloadGrpcProxyTlsKey: String = DevServerSettings.LiveReloadGrpcProxyTlsKey
    val LiveReloadIsDebug: String = DevServerSettings.LiveReloadIsDebug
    val LiveReloadThreadInterruptTimeout: String = DevServerSettings.LiveReloadThreadInterruptTimeout
    // format: on
  }

  val liveFileWatchService =
    settingKey[FileWatchService]("The watch service to catch file changes.")

  val liveHookBundle = taskKey[Option[HookBundle]](
    "If defined, hooks are loaded from predefined set."
  )
  val liveStartupHooks =
    taskKey[Seq[String]]("The list of startup hooks (classnames).")
  val liveShutdownHooks =
    taskKey[Seq[String]]("The list of shutdown hooks (classnames).")

  val livePropagateEnv =
    taskKey[Map[String, String]](
      "Propagates environment variables to a reloadable application."
    )

  val liveServerType =
    settingKey[ServerType]("Server type: HTTP or GRPC.")

  val liveDevSettings =
    settingKey[Seq[(String, String)]]("Development server settings.")

  @transient val liveMonitoredFiles =
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
