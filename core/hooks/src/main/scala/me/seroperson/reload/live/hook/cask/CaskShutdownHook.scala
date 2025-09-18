package me.seroperson.reload.live.hook.cask

import me.seroperson.reload.live.build.BuildLogger
import me.seroperson.reload.live.hook.Hook
import me.seroperson.reload.live.hook.ReflectionUtils
import me.seroperson.reload.live.hook.RuntimeShutdownHook
import me.seroperson.reload.live.settings.DevServerSettings

class CaskShutdownHook extends RuntimeShutdownHook {

  override def description: String = "Shutdown a cask.main.Main"

  override def isAvailable: scala.Boolean =
    ReflectionUtils.hasClass("cask.main.Main")

}
