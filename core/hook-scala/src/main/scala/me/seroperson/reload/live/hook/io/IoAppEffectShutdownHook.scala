package cats.effect

import cats.effect.unsafe.IORuntime
import java.lang.management.ManagementFactory
import javax.management.ObjectName
import me.seroperson.reload.live.build.BuildLogger
import me.seroperson.reload.live.hook.Hook
import me.seroperson.reload.live.hook.ReflectionUtils
import me.seroperson.reload.live.settings.DevServerSettings

/** Shutdown hook specifically designed for Cats Effect IOApp applications.
  *
  * This hook handles the proper shutdown of Cats Effect runtime systems,
  * including cleanup of IORuntime instances and JMX MBean deregistration. It
  * ensures that all IO runtimes are properly shut down and removed from the
  * global registry to prevent resource leaks during live reload cycles.
  *
  * @note
  *   This class is in the `cats.effect` package to access package-private APIs
  */
class IoAppEffectShutdownHook extends Hook {

  override def description: String = "Shutdown a cats.effect.IOApp"

  override def isAvailable: Boolean =
    ReflectionUtils.hasClass("cats.effect.IOApp$")

  override def hook(
      th: Thread,
      cl: ClassLoader,
      settings: DevServerSettings,
      logger: BuildLogger
  ): Unit = {
    val allCollectedRuntimes =
      IORuntime.allRuntimes.unsafeHashtable().filter(_ != null).collect {
        case runtime: IORuntime =>
          runtime.shutdown()
          runtime
      }
    allCollectedRuntimes.foreach { runtime =>
      IORuntime.allRuntimes.remove(runtime, runtime.hashCode())
    }

    // For some reason this observer isn't unregistering automatically
    def unregisterBean(name: String): Unit = {
      try {
        val mBeanServer = ManagementFactory.getPlatformMBeanServer
        val mBeanObjectName = new ObjectName(name)
        mBeanServer.unregisterMBean(mBeanObjectName)
      } catch {
        case ex: Exception =>
          logger.debug(
            s"Error during unregistering monitoring bean: ${ex.getMessage}"
          )
      }
    }

    unregisterBean("cats.effect.metrics:type=CpuStarvation")
    unregisterBean("cats.effect.unsafe.metrics:type=CpuStarvation")
  }

}
