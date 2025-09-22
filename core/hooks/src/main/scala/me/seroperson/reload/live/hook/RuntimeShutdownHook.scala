package me.seroperson.reload.live.hook

import me.seroperson.reload.live.build.BuildLogger
import me.seroperson.reload.live.settings.DevServerSettings

/** Generic runtime shutdown hook that runs application shutdown hooks.
  *
  * This hook is designed to gracefully shut down applications by running all
  * registered shutdown hooks. It's a fallback option that works with any Java
  * application regardless of the specific framework used.
  */
class RuntimeShutdownHook extends Hook {

  override def description: String = "Shutdown a generic application"

  override def isAvailable: Boolean =
    true

  override def hook(settings: DevServerSettings, logger: BuildLogger): Unit = {
    ReflectionUtils.runApplicationShutdownHooks()
  }

}
