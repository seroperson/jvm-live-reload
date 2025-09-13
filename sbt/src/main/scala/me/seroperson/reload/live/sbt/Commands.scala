package me.seroperson.reload.live.sbt

import LiveReloadPlugin.autoImport._
import java.nio.file.Path
import java.util.function.Supplier
import me.seroperson.reload.live.runner.CompileResult
import me.seroperson.reload.live.runner.DevServerRunner
import sbt._
import sbt.Keys._
import sbt.internal.inc.Analysis

object Commands {

  private[this] var commonClassLoader: ClassLoader = _

  val liveReloadTask = Def.task {
    liveCompileEverything.value.reduceLeft(_ ++ _)
  }

  val liveDefaultRunTask =
    Def.inputTask {
      import scala.collection.JavaConverters._

      val interactionMode: Option[PlayInteractionMode] = None
      val args = Def.spaceDelimited().parsed

      val sbtLog = streams.value.log
      val sbtState = state.value
      val scope = resolvedScoped.value.scope
      val interaction = interactionMode.getOrElse(
        PlayConsoleInteractionMode
      )

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
              sbtState
            case _ => sbtState
          }
          PlayReload.compile(
            reloadCompile =
              () => Project.runTask(scope / liveReload, newState).map(_._2).get,
            classpath = () =>
              Project
                .runTask(
                  scope / liveReloaderClasspath,
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

      lazy val devModeServer = DevServerRunner.getInstance.run(
        /* javaOptions */ (Runtime / javaOptions).value.asJava,
        /* commonClassLoader */ liveCommonClassloader.value,
        /* dependencyClasspath */ liveDependencyClasspath.value.files.asJava,
        /* reloadCompile */ reloadCompile,
        /* assetsClassLoader */ liveAssetsClassLoader.value.apply,
        /* triggerReload */ null,
        // avoid monitoring same folder twice or folders that don't exist
        /* monitoredFiles */ liveMonitoredFiles.value.asJava,
        /* fileWatchService */ liveFileWatchService.value,
        /* devSettings */ liveDevSettings.value.toMap.asJava,
        /* args */ args.asJava,
        /* mainClassName */ (Compile / run / mainClass).value.get,
        /* internalMainClassName */ (Compile / mainClass).value.get,
        /* reloadLock */ LiveReloadPlugin,
        /* shutdownHookClasses */ liveShutdownHooks.value.asJava,
        /* logger */ new SbtBuildLogger(sbtLog)
      )

      val serverDidStart = interaction match {
        case nonBlocking: PlayNonBlockingInteractionMode =>
          nonBlocking.start(devModeServer)
        case _ =>
          devModeServer

          import scala.Console.{GREEN, UNDERLINED, RESET, YELLOW}

          sbtLog.info(
            s"🎉 Development Live Reload server successfully started!"
          )
          sbtLog.info(
            s"🚀 Serving at:    ${GREEN}http://localhost:9000${RESET}"
          )
          sbtLog.info(
            s"   Proxifying to: ${GREEN}http://localhost:8080${RESET}"
          )
          sbtLog.info(
            s"ℹ️ Perform a first request to start the underlying server"
          )
          sbtLog.info(s"   Use ${UNDERLINED}Enter${RESET} to stop and exit")

          try {
            interaction.waitForCancel()
          } finally {
            devModeServer.close()
          }
          true
      }
      (interaction, serverDidStart)
    }

  val liveCommonClassloaderTask = Def.task {
    val classpath = (Compile / dependencyClasspath).value
    val log = streams.value.log
    lazy val commonJars: PartialFunction[java.io.File, java.net.URL] = {
      case jar if jar.getName.startsWith("h2-") || jar.getName == "h2.jar" =>
        jar.toURI.toURL
    }

    if (commonClassLoader == null) {
      // The parent of the system classloader *should* be the extension classloader:
      // https://web.archive.org/web/20060127014310/http://www.onjava.com/pub/a/onjava/2005/01/26/classloading.html
      // We use this because this is where things like Nashorn are located. We don't use the system classloader
      // because it will be polluted with the sbt launcher and dependencies of the sbt launcher.
      // See https://github.com/playframework/playframework/issues/3420 for discussion.
      val parent = ClassLoader.getSystemClassLoader.getParent
      log.debug("Using parent loader for play common classloader: " + parent)

      commonClassLoader = new java.net.URLClassLoader(
        classpath.map(_.data).collect(commonJars).toArray,
        parent
      ) {
        override def toString =
          "Common ClassLoader: " + getURLs.map(_.toString).mkString(",")
      }
    }

    commonClassLoader
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

  /*val h2Command = Command.command("h2-browser") { (state: State) =>
    try {
      val commonLoader =
        Project.runTask(playCommonClassloader, state).get._2.toEither.right.get
      val h2ServerClass = commonLoader.loadClass("org.h2.tools.Server")
      h2ServerClass
        .getMethod("main", classOf[Array[String]])
        .invoke(null, Array.empty[String])
    } catch {
      case _: ClassNotFoundException =>
        state.log.error(
          s"""|H2 Dependency not loaded, please add H2 to your Classpath!
              |Take a look at https://www.playframework.com/documentation/${play.core.PlayVersion.current}/Developing-with-the-H2-Database#H2-database on how to do it.""".stripMargin
        )
      case e: Exception => e.printStackTrace()
    }
    state
  }*/

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
