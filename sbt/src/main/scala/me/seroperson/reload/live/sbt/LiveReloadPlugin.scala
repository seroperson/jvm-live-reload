package me.seroperson.reload.live.sbt

import java.util.function.Supplier
import me.seroperson.reload.live.hook.Hook
import me.seroperson.reload.live.webserver.CompileResult
import me.seroperson.reload.live.webserver.DevServerRunner
import play.dev.filewatch.FileWatchService
import play.dev.filewatch.LoggerProxy
import sbt._
import sbt.Keys._
import sbt.internal.inc.Analysis
import scala.collection.JavaConverters._

object LiveReloadPlugin extends AutoPlugin {
  override def trigger = allRequirements

  override def requires = plugins.JvmPlugin

  object autoImport {
    val helloGreeting = settingKey[String]("greeting")
    val hello = taskKey[Unit]("say hello")

    type ClassLoaderCreator = (String, Array[URL], ClassLoader) => ClassLoader

    val fileWatchService =
      settingKey[FileWatchService](
        "The watch service Play uses to watch for file changes"
      )

    val startupHooks = settingKey[Seq[String]]("Startup hooks")
    val shutdownHooks = settingKey[Seq[String]]("Shutdown hooks")

    val devSettings = settingKey[Seq[(String, String)]]("playDevSettings")

    val playMonitoredFiles = taskKey[Seq[File]]("playMonitoredFiles")

    val playDependencyClasspath = taskKey[Classpath](
      "The classpath containing all the jar dependencies of the project"
    )
    val playReloaderClasspath = taskKey[Classpath](
      "The application classpath, containing all projects in this build that are dependencies of this project, including this project"
    )
    val playCommonClassloader = taskKey[ClassLoader](
      "The common classloader, is used to hold H2 to ensure in memory databases don't get lost between invocations of run"
    )
    val playDependencyClassLoader = taskKey[ClassLoaderCreator](
      "A function to create the dependency classloader from a name, set of URLs and parent classloader"
    )
    val playReloaderClassLoader = taskKey[ClassLoaderCreator](
      "A function to create the application classloader from a name, set of URLs and parent classloader"
    )
    val playAssetsClassLoader = taskKey[ClassLoader => ClassLoader](
      "Function that creates a classloader from a given parent that contains all the assets."
    )
    val playReload = taskKey[Analysis](
      "Executed when sources of changed, to recompile (and possibly reload) the app"
    )
    val playCompileEverything = taskKey[Seq[Analysis]](
      "Compiles this project and every project it depends on."
    )
    val playAssetsWithCompilation = taskKey[Analysis](
      "The task that's run on a particular project to compile it. By default, builds assets and runs compile."
    )
  }

  import autoImport._

  override lazy val globalSettings: Seq[Setting[_]] = Seq(
    helloGreeting := "hi"
    // libraryDependencies ++= dependencies
  )

  val playDefaultRunTask =
    playRunTask(
      playDependencyClasspath,
      playReloaderClasspath,
      playAssetsClassLoader
    )

  def playRunTask(
      dependencyClasspath: TaskKey[Classpath],
      reloaderClasspath: TaskKey[Classpath],
      assetsClassLoader: TaskKey[ClassLoader => ClassLoader],
      interactionMode: Option[PlayInteractionMode] = None
  ): Def.Initialize[InputTask[(PlayInteractionMode, Boolean)]] = Def.inputTask {
    val args = Def.spaceDelimited().parsed

    val state = Keys.state.value
    val scope = resolvedScoped.value.scope
    val interaction = interactionMode.getOrElse(
      PlayConsoleInteractionMode
    ) // .getOrElse(playInteractionMode.value)

    val reloadCompile: Supplier[CompileResult] = () => {
      // This code and the below Project.runTask(...) run outside of a user-called sbt command/task.
      // It gets called much later, by code, not by user, when a request comes in which causes Play to re-compile.
      // Since sbt 1.8.0 a LoggerContext closes after command/task that was run by a user is finished.
      // Therefore we need to wrap this code with a new, open LoggerContext.
      // See https://github.com/playframework/playframework/issues/11527

      // var loggerContext: LoggerContext = null
      try {
        val newState = interaction match {
          case _: PlayNonBlockingInteractionMode =>
            /*loggerContext = LoggerContext(useLog4J =
              state.get(Keys.useLog4J.key).getOrElse(false)
            )
            state.put(Keys.loggerContext, loggerContext)*/
            state
          case _ => state
        }
        PlayReload.compile(
          reloadCompile =
            () => Project.runTask(scope / playReload, newState).map(_._2).get,
          classpath = () =>
            Project
              .runTask(
                scope / reloaderClasspath,
                newState // .put(WebKeys.disableExportedProducts, true)
              )
              .map(_._2)
              .get,
          streams = () =>
            Project
              .runTask(scope / streamsManager, newState)
              .map(_._2)
              .get
              .toEither
              .right
              .toOption,
          newState,
          scope
        )
      } finally {
        /*interaction match {
          case _: PlayNonBlockingInteractionMode => loggerContext.close()
          case _                                 => // no-op
        }*/
      }

    }

    lazy val devModeServer = DevServerRunner.startDevMode(
      // runHooks.value.asJava,
      (Runtime / javaOptions).value.asJava,
      playCommonClassloader.value,
      dependencyClasspath.value.files.asJava,
      reloadCompile,
      cls => assetsClassLoader.value.apply(cls),
      null,
      // avoid monitoring same folder twice or folders that don't exist
      playMonitoredFiles.value.distinct.filter(_.exists()).asJava,
      fileWatchService.value,
      // generatedSourceHandlers.asJava,
      baseDirectory.value,
      devSettings.value.toMap.asJava,
      args.asJava,
      (Compile / run / mainClass).value.get,
      (Compile / mainClass).value.get,
      LiveReloadPlugin,
      shutdownHooks.value.asJava
    )

    val serverDidStart = interaction match {
      case nonBlocking: PlayNonBlockingInteractionMode =>
        nonBlocking.start(devModeServer)
      case _ =>
        devModeServer

        println()
        println(
          "(Server started, use Enter to stop and go back to the console...)"
        )
        println()

        try {
          watchContinuously(state) match {
            case Some(watched) =>
              // ~ run mode
              /*interaction.doWithoutEcho {
                twiddleRunMonitor(
                  watched,
                  state,
                  devModeServer.buildLink,
                  Some(PlayWatchState.empty)
                )
              }*/ ()
            case None =>
              // run mode
              interaction.waitForCancel()
          }
        } finally {
          devModeServer.close()
          println()
        }
        true
    }
    (interaction, serverDidStart)
  }

  /** Monitor changes in ~run mode.
    */
  /*@tailrec private def twiddleRunMonitor(
      watched: Watched,
      state: State,
      reloader: BuildLink,
      ws: Option[PlayWatchState] = None
  ): Unit = {
    val ContinuousState =
      AttributeKey[PlayWatchState](
        "watch state",
        "Internal: tracks state for continuous execution."
      )

    def isEOF(c: Int): Boolean = c == 4

    @tailrec def shouldTerminate: Boolean =
      (System.in.available > 0) && (isEOF(System.in.read()) || shouldTerminate)

    val sourcesFinder: Supplier[java.lang.Iterable[java.io.File]] = () => {
      watched
        .watchSources(state)
        .iterator
        .flatMap(new PlaySource(_).getPaths)
        .collect {
          case p if Files.exists(p) => p.toFile
        }
        .toIterable
        .asJava
    }

    val watchState =
      ws.getOrElse(state.get(ContinuousState).getOrElse(PlayWatchState.empty))
    val pollInterval = watched.pollInterval.toMillis.toInt

    val (triggered, newWatchState, newState) =
      try {
        val r =
          PlaySourceModificationWatch.watch(
            sourcesFinder,
            pollInterval,
            watchState,
            () => shouldTerminate
          )
        (r.isTriggered, r.getState, state)
      } catch {
        case e: Exception =>
          val log = state.log
          log.error(
            "Error occurred obtaining files to watch.  Terminating continuous execution..."
          )
          log.trace(e)
          (false, watchState, state.fail)
      }

    if (triggered) {
      // Then launch compile
      Project.synchronized {
        val start = System.currentTimeMillis
        Project.runTask(Compile / compile, newState).get._2.toEither.map { _ =>
          val duration = System.currentTimeMillis - start match {
            case ms if ms < 1000 => ms + "ms"
            case seconds         => (seconds / 1000) + "s"
          }
          println(s"[${Colors.green("success")}] Compiled in $duration")
        }
      }

      // Avoid launching too much compilation
      Thread.sleep(Watched.PollDelay.toMillis)

      // Call back myself
      twiddleRunMonitor(watched, newState, reloader, Some(newWatchState))
    }
  }*/

  private def watchContinuously(state: State): Option[Watched] = {
    for {
      watched <- state.get(Watched.Configuration)
      monitor <- state.get(Watched.ContinuousEventMonitor)
      if monitor.state.count > 0 // assume we're in ~ run mode
    } yield watched
  }

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    hello := {
      val s = streams.value
      val g = helloGreeting.value
      s.log.info(g)
    },
    libraryDependencies ++= Seq(
      "me.seroperson" %% "jvm-live-reload-webserver" % "0.0.1"
      // "me.seroperson" %% "jvm-live-reload-hooks" % "0.0.1"
    ),
    fileWatchService := {
      FileWatchService.defaultWatchService(
        target.value,
        pollInterval.value.toMillis.toInt,
        null.asInstanceOf[LoggerProxy]
      )
    },
    playAssetsClassLoader := { (parent: ClassLoader) =>
      parent
    },
    devSettings := Nil,
    // libraryDependencies ++= dependencies,
    playMonitoredFiles := Commands.playMonitoredFilesTask.value,
    // all dependencies from outside the project (all dependency jars)
    playDependencyClasspath := (Runtime / externalDependencyClasspath).value,
    // all user classes, in this project and any other subprojects that it depends on
    playReloaderClasspath := Classpaths
      .concatDistinct(
        Runtime / exportedProducts,
        Runtime / internalDependencyClasspath
      )
      .value,
    // filter out asset directories from the classpath (supports sbt-web 1.0 and 1.1)
    playReloaderClasspath ~= {
      _.filter(_ => true) // .filter(_.get(WebKeys.webModulesLib.key).isEmpty)
    },
    playCommonClassloader := Commands.playCommonClassloaderTask.value,
    playReload := Commands.playReloadTask.value,
    playCompileEverything := Commands.playCompileEverythingTask.value
      .asInstanceOf[Seq[Analysis]],
    shutdownHooks := Seq("me.seroperson.reload.live.hook.ZioHttpShutdownHook"),
    Compile / Keys.run := playDefaultRunTask.evaluated,
    Compile / Keys.run / mainClass := Some(
      "me.seroperson.reload.live.webserver.DevServerStart"
    )
  )
}
