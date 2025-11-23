package me.seroperson.reload.live.mill

import java.util.function.Supplier
import me.seroperson.reload.live.runner.CompileResult
import me.seroperson.reload.live.runner.CompileResult.CompileFailure
import me.seroperson.reload.live.runner.CompileResult.CompileSuccess
import me.seroperson.reload.live.runner.DevServerRunner
import me.seroperson.reload.live.runner.StartParams
import me.seroperson.reload.live.settings.DevServerSettings
import mill.*
import mill.api.Evaluator
import mill.api.Task.Simple
import mill.api.TaskCtx
import mill.api.daemon.Result
import mill.javalib.Dep
import mill.javalib.JavaModule
import mill.javalib.api.CompilationResult
import mill.scalalib.*
import play.dev.filewatch.FileWatchService
import play.dev.filewatch.LoggerProxy
import scala.jdk.CollectionConverters.*

trait LiveReloadModule extends JavaModule {

  override def mvnDeps =
    super.mvnDeps() ++ Seq(
      mvn"me.seroperson:jvm-live-reload-webserver:${BuildInfo.version}",
      mvn"me.seroperson::jvm-live-reload-hook-scala:${BuildInfo.version}"
    )

  def liveDevSettings: Task[Seq[(String, String)]] = Task.Anon {
    Seq()
  }

  def liveHookBundle: Task[Option[HookBundle]] = Task.Anon {
    runClasspath().collectFirst {
      case lib if lib.path.toIO.getName.startsWith("zio-http") =>
        ZioAppHookBundle
      case lib if lib.path.toIO.getName.startsWith("http4s") =>
        IoAppHookBundle
      case lib if lib.path.toIO.getName.startsWith("cask") =>
        CaskAppHookBundle
    }
  }

  def liveStartupHooks: Task[Seq[String]] = Task.Anon {
    liveHookBundle() match {
      case Some(hookBundle) => hookBundle.startupHooks
      case None             =>
        Seq(
          HookClassnames.RestApiHealthCheckStartup
        )
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
      eval.execute(Seq(compile)) match {
        case Evaluator.Result(watched, Result.Failure(err), _, _) =>
          new CompileFailure(new Throwable(err))

        case Evaluator.Result(
              watched,
              Result.Success(_),
              selectedTasks,
              executionResults
            ) =>
          val allClasses = executionResults.transitiveResults.flatMap {
            case (key, value) =>
              value.asSuccess.map(_.value.value).collect {
                case x: CompilationResult =>
                  x.classes.path.toIO
              }
          }
          new CompileSuccess(allClasses.toList.asJava)
      }
    }

    val taskLog = Task.log
    val logger = new MillBuildLogger(settings, taskLog)
    val fileWatchService = FileWatchService.detect(
      100 /* 0.1 sec */,
      null.asInstanceOf[LoggerProxy]
    )

    val params = new StartParams(
      /* settings */ settings,
      /* dependencyClasspath */ resolvedRunMvnDeps()
        .map(_.path.toIO)
        .asJava,
      /* monitoredFiles */ sources().map(_.path.toIO).asJava,
      /* mainClassName */ "me.seroperson.reload.live.webserver.DevServerStart",
      /* internalMainClassName */ finalMainClass(),
      /* startupHookClasses */ liveStartupHooks().asJava,
      /* shutdownHookClasses */ liveShutdownHooks().asJava
    )

    val devServerRunner = DevServerRunner.getInstance
    devServerRunner.runBlocking(
      params,
      reloadCompile,
      /* triggerReload */ null,
      fileWatchService,
      logger,
      taskLog.streams.in,
      taskLog.streams.out
    )
  }

}

object LiveReloadModule
