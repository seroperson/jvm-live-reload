package me.seroperson.reload.live.hook.zio;

import me.seroperson.reload.live.hook.ReflectionUtils
import me.seroperson.reload.live.hook.RuntimeShutdownHook

/** Shutdown hook specifically designed for ZIO applications.
  *
  * This hook extends the generic runtime shutdown hook and is only available
  * when the ZIO framework (`zio.ZIOApp$`) is present on the classpath. It
  * provides ZIO-specific shutdown behavior while leveraging the standard
  * runtime shutdown hook functionality.
  *
  * @note
  *   Future implementations might use ZIO-specific shutdown mechanisms like
  *   `zio.ZIOAppPlatformSpecific.interruptRootFibers`
  */
class ZioAppShutdownHook extends RuntimeShutdownHook {

  override def description: String = "Shutdown a zio.ZIOApp"

  override def isAvailable: Boolean =
    ReflectionUtils.hasClass("zio.ZIOApp$")

  // Shutdown code from zio.ZIOAppPlatformSpecific.interruptRootFibers also may work
}
