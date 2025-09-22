package me.seroperson.reload.live.hook.cask

import me.seroperson.reload.live.build.BuildLogger
import me.seroperson.reload.live.hook.Hook
import me.seroperson.reload.live.hook.ReflectionUtils
import me.seroperson.reload.live.hook.RuntimeShutdownHook
import me.seroperson.reload.live.settings.DevServerSettings

/** Shutdown hook specifically designed for Cask web framework applications.
  *
  * This hook extends the generic runtime shutdown hook and is only available
  * when the Cask framework (`cask.main.Main`) is present on the classpath. It
  * provides Cask-specific shutdown behavior while leveraging the standard
  * runtime shutdown hook functionality.
  */
class CaskShutdownHook extends RuntimeShutdownHook {

  override def description: String = "Shutdown a cask.main.Main"

  override def isAvailable: scala.Boolean =
    ReflectionUtils.hasClass("cask.main.Main")

}
