package me.seroperson.reload.live.hook.zio;

import me.seroperson.reload.live.hook.ReflectionUtils
import me.seroperson.reload.live.hook.RuntimeShutdownHook

class ZioAppShutdownHook extends RuntimeShutdownHook {

  override def description: String = "Shutdown a zio.ZIOApp"

  override def isAvailable: Boolean =
    ReflectionUtils.hasClass("zio.ZIOApp$")

  // Shutdown code from zio.ZIOAppPlatformSpecific.interruptRootFibers also may work
}
