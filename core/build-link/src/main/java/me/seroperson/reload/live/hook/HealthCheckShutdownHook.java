package me.seroperson.reload.live.hook;

import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

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
  public void hook(DevServerSettings settings, BuildLogger logger) {
    try {
      while (isHealthy(settings.getHttpHost(), settings.getHttpPort())) {
        logger.debug("Waiting for the health-check to return false ...");
        Thread.sleep(50L);
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
      return;
    }
  }

}
