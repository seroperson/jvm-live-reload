package me.seroperson.reload.live.sbt

import LiveReloadPlugin.autoImport.*
import java.nio.file.Path
import java.util.function.Supplier
import me.seroperson.reload.live.runner.CompileResult
import me.seroperson.reload.live.runner.DevServerRunner
import me.seroperson.reload.live.settings.DevServerSettings
import sbt.*
import sbt.Keys.*
import sbt.internal.inc.Analysis
import sbt.util.LoggerContext
import scala.jdk.CollectionConverters.*
import xsbti.FileConverter

/** Object containing SBT task and command implementations for live reload
  * functionality.
  *
  * This object provides the core implementation of SBT tasks and commands that
  * enable live reload during development. It manages compilation, class
  * loading, file monitoring, and server lifecycle operations.
  */
object Commands {

  val liveReloadTask = Def.task {
    liveCompileEverything.value.reduceLeft(_ ++ _)
  }

  val liveDefaultRunTask = liveRunTask(None)

  def liveRunTask(interactionArg: Option[InteractionMode]) = Def.inputTask {
    implicit val fc: FileConverter = fileConverter.value

    val args = Def.spaceDelimited().parsed

    val sbtLog = streams.value.log
    val sbtState = state.value
    val scope = resolvedScoped.value.scope
    val interaction = interactionArg.getOrElse(liveInteractionMode.value)

    val reloadCompile: Supplier[CompileResult] = () => {
      // This code and the below Project.runTask(...) run outside of a user-called sbt command/task.
      // It gets called much later, by code, not by user, when a request comes in which causes us to re-compile.
      // Since sbt 1.8.0 a LoggerContext closes after command/task that was run by a user is finished.
      // Therefore we need to wrap this code with a new, open LoggerContext.
      // See https://github.com/playframework/playframework/issues/11527

      var loggerContext: LoggerContext = null
      try {
        val newState = interaction match {
          case _: NonBlockingInteractionMode =>
            loggerContext = SbtLoggerContextAccess(sbtState)
            sbtState.put(
              SbtLoggerContextAccess.loggerContextKey,
              loggerContext
            )
          case _ =>
            sbtState
        }

        (for {
          analysis <- SbtCompat
            .runTask(scope / liveReload, newState)
            .map(_._2)
            .get
            .toEither
            .right
          classpath <- SbtCompat
            .runTask(
              scope / liveReloaderClasspath,
              newState
            )
            .map(_._2)
            .get
            .toEither
            .right
        } yield new CompileResult.CompileSuccess(
          SbtCompat.getFiles(classpath).asJava
        )).left
          .map(inc => {
            inc.directCause.map(_.printStackTrace())
            new CompileResult.CompileFailure(new Throwable("Error"))
          })
          .merge
      } finally {
        interaction match {
          case _: NonBlockingInteractionMode => loggerContext.close()
          case _                             => // no-op
        }
      }
    }

    val settings = new DevServerSettings(
      (Runtime / javaOptions).value.asJava,
      args.asJava,
      liveDevSettings.value.toMap.asJava
    );

    val logger = new SbtBuildLogger(settings, sbtLog)
    lazy val devModeServer = DevServerRunner.getInstance.run(
      /* settings */ settings,
      /* dependencyClasspath */ SbtCompat
        .getFiles(liveDependencyClasspath.value)
        .asJava,
      /* reloadCompile */ reloadCompile,
      /* triggerReload */ null,
      /* monitoredFiles */ liveMonitoredFiles.value.asJava,
      /* fileWatchService */ liveFileWatchService.value,
      /* mainClassName */ (Compile / run / mainClass).value.get,
      /* internalMainClassName */ (Compile / mainClass).value.get,
      /* reloadLock */ LiveReloadPlugin,
      /* startupHookClasses */ liveStartupHooks.value.asJava,
      /* shutdownHookClasses */ liveShutdownHooks.value.asJava,
      /* logger */ logger
    )

    val serverDidStart = interaction match {
      case nonBlocking: NonBlockingInteractionMode =>
        nonBlocking.start(devModeServer)
      case _ =>
        devModeServer

        import scala.Console.{GREEN, UNDERLINED, RESET, YELLOW}

        sbtLog.info(
          s"🎉 Development Live Reload server successfully started!"
        )
        sbtLog.info(
          s"🚀 Serving at:    ${GREEN}http://${settings.getProxyHttpHost}:${settings.getProxyHttpPort}${RESET}"
        )
        sbtLog.info(
          s"   Proxifying to: ${GREEN}http://${settings.getHttpHost}:${settings.getHttpPort}${RESET}"
        )
        sbtLog.info(
          s"ℹ️ Perform a first request to start the underlying server"
        )
        sbtLog.info(s"   Use ${UNDERLINED}Enter${RESET} to stop and exit")

        try {
          logger.debug("Before waitForCancel")
          interaction.waitForCancel()
          logger.debug("After waitForCancel")
        } finally {
          logger.debug("Running devModeServer.close()")
          devModeServer.close()
        }
        true
    }
    (interaction, serverDidStart)
  }

  val liveBgRunTask = Def.inputTask {
    bgJobService.value.runInBackground(resolvedScoped.value, state.value) {
      (logger, workingDir) =>
        liveRunTask(
          Some(StaticNonBlockingInteractionMode)
        ).evaluated match {
          case (mode: NonBlockingInteractionMode, serverDidStart) =>
            if (serverDidStart) {
              try {
                Thread.sleep(
                  Long.MaxValue
                )
              } catch {
                case _: InterruptedException =>
                  mode.stop() // shutdown dev server
              }
            }
        }
    }
  }

  val liveCompileEverythingTask = Def.taskDyn {
    Def
      .taskDyn(Compile / compile)
      .all(
        ScopeFilter(
          inDependencies(thisProjectRef.value)
        )
      )
  }

  val liveMonitoredFilesTask = Def.taskDyn {
    val projectRef = thisProjectRef.value

    def filter = ScopeFilter(
      inDependencies(projectRef),
      inConfigurations(Compile)
    )

    Def.task {
      val allDirectories =
        (unmanagedSourceDirectories ?? Nil).all(filter).value.flatten ++
          (unmanagedResourceDirectories ?? Nil).all(filter).value.flatten

      val existingDirectories = allDirectories.filter(_.exists)

      // Filter out directories that are sub paths of each other, by sorting them lexicographically, then folding, excluding
      // entries if the previous entry is a sub path of the current
      val distinctDirectories = existingDirectories
        .map(_.getCanonicalFile.toPath)
        .sorted
        .foldLeft(List.empty[Path]) { (result, next) =>
          result.headOption match {
            case Some(previous) if next.startsWith(previous) => result
            case _                                           => next :: result
          }
        }

      distinctDirectories
        .map(_.toFile)
        .distinct
        .filter(_.exists())
    }
  }
}
