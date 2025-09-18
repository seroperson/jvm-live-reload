package zio;

import me.seroperson.reload.live.build.BuildLogger
import me.seroperson.reload.live.hook.Hook
import me.seroperson.reload.live.hook.ReflectionUtils
import me.seroperson.reload.live.settings.DevServerSettings

// fiber.isAlive is private[zio], so we have to put this class into the `zio` package
class ZioZioAppShutdownHook extends Hook {

  override def description: String = "Shutdown an zio.ZIOApp"

  override def isAvailable: Boolean =
    ReflectionUtils.hasClass("zio.ZIOApp$")

  override def hook(settings: DevServerSettings, logger: BuildLogger): Unit = {
    implicit val unsafe = Unsafe.unsafe
    zio.Runtime.default.unsafe.run {
      // Shutdown code from zio.ZIOAppPlatformSpecific.interruptRootFibers
      for {
        currentFiberId <- ZIO.fiberId
        roots <- Fiber.roots
        _ <- Fiber.interruptAll(
          roots.view.filter(fiber =>
            fiber.isAlive() && (fiber.id != currentFiberId)
          )
        )
      } yield ()
    }
  }

}
