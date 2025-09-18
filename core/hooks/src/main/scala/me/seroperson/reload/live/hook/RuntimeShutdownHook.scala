package me.seroperson.reload.live.hook

import me.seroperson.reload.live.build.BuildLogger
import me.seroperson.reload.live.settings.DevServerSettings

class RuntimeShutdownHook extends Hook {

  override def description: String = "Shutdown a generic application"

  override def isAvailable: Boolean =
    true

  override def hook(settings: DevServerSettings, logger: BuildLogger): Unit = {
    ReflectionUtils.runApplicationShutdownHooks()
  }

}
