package zio;

// fiber.isAlive is private[zio]
class ZioZioHttpShutdownHook extends me.seroperson.reload.live.hook.Hook {

  override def hook(): Unit = {
    java.lang.System.out.println("Running ZioZioHttpShutdownHook")
    if (isClass("zio.http.Server")) {
      implicit val unsafe = Unsafe.unsafe
      zio.Runtime.default.unsafe.run {
        for {
          currentFiberId <- ZIO.fiberId
          // zio.ZIOAppPlatformSpecific.interruptRootFibers
          roots <- Fiber.roots
          _ <- Fiber.interruptAll(
            roots.view.filter(fiber =>
              fiber.isAlive() && (fiber.id != currentFiberId)
            )
          )
        } yield ()
      }
      java.lang.System.out.println("Finished ZioZioHttpShutdownHook")
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
