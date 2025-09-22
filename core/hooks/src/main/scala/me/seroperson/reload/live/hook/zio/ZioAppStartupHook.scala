package me.seroperson.reload.live.hook.zio;

import java.io.Closeable
import java.util.concurrent.ThreadPoolExecutor
import me.seroperson.reload.live.build.BuildLogger
import me.seroperson.reload.live.hook.Hook
import me.seroperson.reload.live.hook.ReflectionUtils
import me.seroperson.reload.live.hook.RuntimeShutdownHook
import me.seroperson.reload.live.settings.DevServerSettings
import zio.Executor

class ZioAppStartupHook extends Hook {

  override def description: String = "Starts a zio.ZIOApp"

  override def isAvailable: Boolean =
    ReflectionUtils.hasClass("zio.ZIOApp$")

  override def hook(
      th: Thread,
      cl: ClassLoader,
      settings: DevServerSettings,
      logger: BuildLogger
  ) = {
    // We need to update Context ClassLoader on all ZScheduler workers because they usually survive reload

    val threadGroup = Thread.currentThread.getThreadGroup
    val threads = new Array[Thread](threadGroup.activeCount)
    threadGroup.enumerate(threads)
    logger.debug(s"Got ${threadGroup.activeCount} active threads.")
    val filteredCount = threads
      .filter(v =>
        v != null && (v.getName.startsWith("ZScheduler") || v.getName
          .startsWith("zio-"))
      )
      .map(v => {
        v.setContextClassLoader(cl)
      })
      .length
    logger.debug(
      s"Setting $cl as a context classloader for $filteredCount threads"
    )
  }

}
