package me.seroperson.reload.live.hook.io;

import cats.effect.IOApp
import cats.effect.unsafe.IORuntime
import java.lang.management.ManagementFactory
import javax.management.ObjectName
import me.seroperson.reload.live.build.BuildLogger
import me.seroperson.reload.live.hook.Hook
import me.seroperson.reload.live.hook.ReflectionUtils
import me.seroperson.reload.live.settings.DevServerSettings

class IoAppShutdownHook extends Hook {

  override def description: String = "Shutdown an cats.effect.IOApp"

  override def isAvailable: Boolean =
    ReflectionUtils.hasClass("cats.effect.IOApp$")

  override def hook(settings: DevServerSettings, logger: BuildLogger): Unit = {
    IORuntime.global.shutdown()

    // For some reason this observer isn't unregistering automatically
    val mBeanServer = ManagementFactory.getPlatformMBeanServer
    val mBeanObjectName = new ObjectName(
      "cats.effect.unsafe.metrics:type=CpuStarvation"
    )
    mBeanServer.unregisterMBean(mBeanObjectName)
  }

}
