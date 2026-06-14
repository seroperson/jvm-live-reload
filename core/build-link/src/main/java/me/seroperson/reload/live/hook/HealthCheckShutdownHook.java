package me.seroperson.reload.live.hook;

import me.seroperson.reload.live.UnrecoverableException;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Abstract base class for shutdown hooks that wait for health checks to fail.
 *
 * <p>This hook continuously polls the server's health status and waits until the health check
 * returns false, indicating the server has shut down properly. This is useful for ensuring clean
 * shutdown procedures in development environments.
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
      long deadlineMs = System.currentTimeMillis() + settings.getThreadInterruptTimeoutMs();
      while (true) {
        if (System.currentTimeMillis() > deadlineMs) {
          throw new UnrecoverableException(
              "Shutdown health-check did not report failure within "
                  + settings.getThreadInterruptTimeoutMs()
                  + "ms; the old application may still be running.");
        }
        logger.debug("Waiting for the health-check to return failure ...");
        var path = settings.getHealthCheckPath();
        var healthResponse =
            isHealthy(logger, path, settings.getHttpHost(), settings.getHttpPort());
        if (healthResponse == 1) {
          // success — server still up
          Thread.sleep(50L);
        } else if (healthResponse == 0) {
          // non-success response, but not an exception
          Thread.sleep(50L);
        } else if (healthResponse == -1) {
          // connection exception, that's what we're looking for
          return;
        } else if (healthResponse == 404) {
          // if health check isn't implemented, don't poll it
          throw new UnrecoverableException(
              "Health-check route " + path + " responded with 404. Is it implemented?");
        }
      }
    } catch (InterruptedException e) {
      // Don't print anything, just quit
    }
  }
}
