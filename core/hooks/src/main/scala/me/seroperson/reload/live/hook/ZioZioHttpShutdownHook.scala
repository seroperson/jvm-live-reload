package zio;

import me.seroperson.reload.live.hook.Hook

// fiber.isAlive is private[zio], so we have to put this class into the `zio` package
class ZioZioHttpShutdownHook extends Hook {

  override def hook(): Unit = {
    if (isClass("zio.http.Server")) {
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

  private def isClass(className: String): Boolean =
    try {
      Class.forName(className)
      true
    } catch {
      case e: ClassNotFoundException =>
        false
    }

}
