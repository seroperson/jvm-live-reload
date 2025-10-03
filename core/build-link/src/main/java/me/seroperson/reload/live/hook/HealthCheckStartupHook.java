package me.seroperson.reload.live.hook;

import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Abstract base class for startup hooks that wait for health checks to succeed.
 *
 * <p>
 * This hook continuously polls the server's health status and waits until the health check returns
 * true, indicating the server has started successfully. This is useful for ensuring the server is
 * ready to handle requests before proceeding with other operations.
 */
public abstract class HealthCheckStartupHook implements HealthCheckHook {

  @Override
  public String description() {
    return "Waits for health-check to return true";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public void hook(Thread th, ClassLoader cl, DevServerSettings settings, BuildLogger logger) {
    try {
      while (!isHealthy(settings.getHealthCheckPath(), settings.getHttpHost(),
          settings.getHttpPort())) {
        logger.debug("Waiting for the health-check to return true ...");
        Thread.sleep(50L);
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

}
