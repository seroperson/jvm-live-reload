package me.seroperson.reload.live.hook.io;

import me.seroperson.reload.live.build.BuildLogger
import me.seroperson.reload.live.hook.Hook
import me.seroperson.reload.live.hook.ReflectionUtils
import me.seroperson.reload.live.settings.DevServerSettings

class IoAppStartupHook extends Hook {

  override def description: String = "Starts a cats.effect.IOApp"

  override def isAvailable: Boolean =
    ReflectionUtils.hasClass("cats.effect.IOApp$")

  override def hook(settings: DevServerSettings, logger: BuildLogger): Unit = {
    // Disables "IOApp `main` is running on a thread other than the main thread" warnings
    System.setProperty("cats.effect.warnOnNonMainThreadDetected", "false");
  }

}
