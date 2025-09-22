package me.seroperson.reload.live.hook;

import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Abstract base class for shutdown hooks that wait for health checks to fail.
 *
 * <p>
 * This hook continuously polls the server's health status and waits until the health check returns
 * false, indicating the server has shut down properly. This is useful for ensuring clean shutdown
 * procedures in development environments.
 */
public abstract class HealthCheckShutdownHook implements HealthCheckHook {

  @Override
  public String description() {
    return "Waits for health-check to return false";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public void hook(Thread th, ClassLoader cl, DevServerSettings settings, BuildLogger logger) {
    try {
      logger.debug("Starting HealthCheckShutdownHook");
      while (isHealthy(settings.getHealthCheckPath(), settings.getHttpHost(),
          settings.getHttpPort())) {
        logger.debug("Waiting for the health-check to return false ...");
        Thread.sleep(50L);
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
      return;
    }
  }

}
