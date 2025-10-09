package zio;

import me.seroperson.reload.live.build.BuildLogger
import me.seroperson.reload.live.hook.Hook
import me.seroperson.reload.live.hook.ReflectionUtils
import me.seroperson.reload.live.hook.RuntimeShutdownHook
import me.seroperson.reload.live.settings.DevServerSettings

class ZioZioAppShutdownHook extends Hook {

  override def description: String = "Shutdown a zio.ZIOApp"

  override def isAvailable: Boolean =
    ReflectionUtils.hasClass("zio.ZIOApp$")

  override def hook(
      th: Thread,
      cl: ClassLoader,
      settings: DevServerSettings,
      logger: BuildLogger
  ) = {
    // Actually it's usually unnecessary from what I saw
    shutdownExecutor(zio.Runtime.defaultExecutor)
    shutdownExecutor(zio.Runtime.defaultBlockingExecutor)
    shutdownExecutor(zio.internal.Blocking.blockingExecutor)

    def shutdownExecutor(executor: Executor) = {
      import java.util.concurrent.ThreadPoolExecutor

      logger.debug("Shutting down zio executor: " + executor)
      executor.asJava match {
        case v: ThreadPoolExecutor => v.shutdownNow()
        case v: AutoCloseable      => v.close()
        case _                     => ()
      }
    }

    /*logger.debug("Interrupting all alive fibers")
    implicit val unsafe = Unsafe.unsafe
    Runtime.default.unsafe.run {
      for {
        mainFiberId <- ZIO.fiberId
        roots <- Fiber.roots
        _ <- Fiber.interruptAll(
          roots.view.filter(fiber =>
            fiber.isAlive() && (fiber.id != mainFiberId)
          )
        )
      } yield ()
    }*/
  }

}
