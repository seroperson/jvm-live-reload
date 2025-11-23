package me.seroperson.reload.live.sbt

import LiveReloadPlugin.autoImport.*
import java.nio.file.Path
import java.util.function.Supplier
import me.seroperson.reload.live.runner.CompileResult
import me.seroperson.reload.live.runner.DevServer
import me.seroperson.reload.live.runner.DevServerRunner
import me.seroperson.reload.live.runner.StartParams
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
private[sbt] object Commands {

  val liveReloadTask = Def.task {
    liveCompileEverything.value.reduceLeft(_ ++ _)
  }

  val liveDefaultRunTask = liveRunTask(isBackground = false)

  def liveRunTask(isBackground: Boolean): Def.Initialize[InputTask[DevServer]] =
    Def.inputTask {
      implicit val fc: FileConverter = fileConverter.value

      val args = Def.spaceDelimited().parsed

      val sbtLog = streams.value.log
      val sbtState = state.value
      val scope = resolvedScoped.value.scope

      val reloadCompile: Supplier[CompileResult] = () => {
        // This code and the below Project.runTask(...) run outside of a user-called sbt command/task.
        // It gets called much later, by code, not by user, when a request comes in which causes us to re-compile.
        // Since sbt 1.8.0 a LoggerContext closes after command/task that was run by a user is finished.
        // Therefore we need to wrap this code with a new, open LoggerContext.
        // See https://github.com/playframework/playframework/issues/11527

        var loggerContext: LoggerContext = null
        try {
          val newState = if (isBackground) {
            loggerContext = SbtLoggerContextAccess(sbtState)
            sbtState.put(
              SbtLoggerContextAccess.loggerContextKey,
              loggerContext
            )
          } else {
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
          if (isBackground) {
            loggerContext.close()
          }
        }
      }

      val settings = new DevServerSettings(
        (Runtime / javaOptions).value.asJava,
        args.asJava,
        liveDevSettings.value.toMap.asJava
      )

      val logger = new SbtBuildLogger(settings, sbtLog)
      val params = new StartParams(
        settings,
        /* dependencyClasspath */ SbtCompat
          .getFiles(liveDependencyClasspath.value)
          .asJava,
        liveMonitoredFiles.value.asJava,
        /* mainClassName */ (Compile / run / mainClass).value.get,
        /* internalMainClassName */ (Compile / mainClass).value.get,
        liveStartupHooks.value.asJava,
        liveShutdownHooks.value.asJava
      )

      val devServerRunner = DevServerRunner.getInstance
      if (isBackground) {
        devServerRunner.runBackground(
          params,
          reloadCompile,
          /* triggerReload */ null,
          liveFileWatchService.value,
          logger
        )
      } else {
        devServerRunner.runBlocking(
          params,
          reloadCompile,
          /* triggerReload */ null,
          liveFileWatchService.value,
          logger,
          System.in,
          System.out
        )
      }
    }

  val liveBgRunTask = Def.inputTask {
    bgJobService.value.runInBackground(resolvedScoped.value, state.value) {
      (logger, workingDir) =>
        val devServer = liveRunTask(isBackground = true).evaluated
        try {
          Thread.sleep(Long.MaxValue)
        } catch {
          case _: InterruptedException =>
            devServer.close()
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
