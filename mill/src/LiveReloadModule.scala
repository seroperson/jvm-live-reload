package me.seroperson.reload.live.mill

import java.io.File
import java.nio.file.{Path => JPath}
import java.util.function.Supplier
import me.seroperson.reload.live.runner.CompileResult
import me.seroperson.reload.live.runner.CompileResult.CompileFailure
import me.seroperson.reload.live.runner.CompileResult.CompileSuccess
import me.seroperson.reload.live.runner.DevServerRunner
import me.seroperson.reload.live.runner.StartParams
import me.seroperson.reload.live.settings.DevServerSettings
import mill.*
import mill.api.BuildCtx
import mill.api.Evaluator
import mill.api.daemon.Result
import mill.javalib.Dep
import mill.javalib.JavaModule
import mill.javalib.api.CompilationResult
import mill.scalalib.*
import play.dev.filewatch.FileWatchService
import play.dev.filewatch.LoggerProxy
import scala.jdk.CollectionConverters.*

trait LiveReloadModule extends JavaModule {

  def liveServerType: Task[ServerType] = Task.Anon {
    HttpServerType
  }

  override def mvnDeps =
    super.mvnDeps() ++ {
      val webserverDep = liveServerType() match {
        case HttpServerType =>
          mvn"me.seroperson:jvm-live-reload-webserver:${BuildInfo.version}"
        case GrpcServerType =>
          mvn"me.seroperson:jvm-live-reload-webserver-grpc:${BuildInfo.version}"
      }
      Seq(
        webserverDep,
        mvn"me.seroperson::jvm-live-reload-hook-scala:${BuildInfo.version}"
      )
    }

  def liveDevSettings: Task[Seq[(String, String)]] = Task.Anon {
    Seq()
  }

  def livePropagateEnv: Task[Map[String, String]] = Task.Anon {
    Map()
  }

  private def liveMonitoredFiles: Task[Seq[File]] = {
    val monitoredInputTasks =
      transitiveRunModuleDeps.flatMap(module =>
        Seq(module.sources, module.resources)
      )
    val monitoredInputs = Task.sequence(monitoredInputTasks)

    Task.Anon {
      val outputRoot =
        (BuildCtx.workspaceRoot / "out").toIO.getCanonicalFile.toPath
      val monitoredPaths = monitoredInputs().flatten
        .map(_.path.toIO.getCanonicalFile.toPath)
        .filterNot(path => path.startsWith(outputRoot))
        .filter(path => path.toFile.exists())
        .distinct
        .sorted
        .foldLeft(List.empty[JPath]) { (result, next) =>
          result.headOption match {
            case Some(previous) if next.startsWith(previous) => result
            case _                                           => next :: result
          }
        }

      monitoredPaths.reverse.map(_.toFile)
    }
  }

  def liveHookBundle: Task[Option[HookBundle]] = Task.Anon {
    if (liveServerType() == GrpcServerType) {
      Some(GrpcAppHookBundle)
    } else {
      val jarNames = runClasspath().map(_.path.toIO.getName)
      def has(prefix: String): Boolean = jarNames.exists(_.startsWith(prefix))
      if (has("zio-http")) Some(ZioAppHookBundle)
      else if (has("http4s")) Some(IoAppHookBundle)
      else if (has("cask")) Some(CaskAppHookBundle)
      else if (has("zio")) Some(ZioAppHookBundle)
      else if (has("cats-effect")) Some(IoAppHookBundle)
      else None
    }
  }

  def liveStartupHooks: Task[Seq[String]] = Task.Anon {
    liveHookBundle() match {
      case Some(hookBundle) => hookBundle.startupHooks
      case None             => Seq(HookClassnames.RestApiHealthCheckStartup)
    }
  }

  def liveShutdownHooks: Task[Seq[String]] = Task.Anon {
    liveHookBundle() match {
      case Some(hookBundle) => hookBundle.shutdownHooks
      case None             =>
        Seq(
          HookClassnames.ThreadInterruptShutdown,
          HookClassnames.RestApiHealthCheckShutdown
        )
    }
  }

  def liveReloadRun(
      eval: Evaluator,
      args: Task[Args] = Task.Anon(Args())
  ): Command[Unit] = Task.Command(exclusive = true) {
    val settings = new DevServerSettings(
      javacOptions().asJava,
      args().value.asJava,
      liveDevSettings().toMap.asJava
    )

    val reloadCompile: Supplier[CompileResult] = () => {
      eval.execute(Seq(runClasspath)) match {
        case Evaluator.Result(_, Result.Failure(err, _, _, _, _, _), _, _) =>
          new CompileFailure(new Throwable(err))

        case Evaluator.Result(
              /* watched */ _,
              Result.Success(_),
              selectedTasks,
              executionResults
            ) =>
          val runClasspathTask = selectedTasks.last
          val classpath = executionResults.transitiveResults
            .collect {
              case (key, value) if key == runClasspathTask =>
                value.asSuccess.map(_.value.value).map { result =>
                  result.asInstanceOf[Seq[PathRef]].map(_.path.toIO)
                }
            }
            .flatten
            .flatten
          new CompileSuccess(classpath.toList.asJava)
      }
    }

    val taskLog = Task.log
    val logger = new MillBuildLogger(settings, taskLog)
    val fileWatchService = FileWatchService.detect(
      100 /* 0.1 sec */,
      null.asInstanceOf[LoggerProxy]
    )

    val mainClassName = liveServerType() match {
      case HttpServerType =>
        "me.seroperson.reload.live.webserver.DevServerStart"
      case GrpcServerType =>
        "me.seroperson.reload.live.webserver.grpc.GrpcDevServerStart"
    }

    val params = new StartParams(
      settings,
      /* dependencyClasspath */ resolvedRunMvnDeps()
        .map(_.path.toIO)
        .asJava,
      /* monitoredFiles */
      liveMonitoredFiles().asJava,
      /* mainClassName */ mainClassName,
      /* internalMainClassName */ finalMainClass(),
      liveStartupHooks().asJava,
      liveShutdownHooks().asJava,
      livePropagateEnv().asJava
    )

    val devServerRunner = DevServerRunner.getInstance
    devServerRunner.runBlocking(
      params,
      reloadCompile,
      /* triggerReload */ null,
      fileWatchService,
      logger,
      System.in,
      System.out
    )
  }

}

object LiveReloadModule
