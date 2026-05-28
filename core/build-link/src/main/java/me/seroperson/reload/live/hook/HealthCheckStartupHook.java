package me.seroperson.reload.live.hook;

import me.seroperson.reload.live.UnrecoverableException;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Abstract base class for startup hooks that wait for health checks to succeed.
 *
 * <p>This hook continuously polls the server's health status and waits until the health check
 * returns true, indicating the server has started successfully. This is useful for ensuring the
 * server is ready to handle requests before proceeding with other operations.
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
    var path = settings.getHealthCheckPath();
    long timeout = settings.getStartupTimeoutMs();
    long deadline = System.currentTimeMillis() + timeout;
    try {
      while (true) {
        AppFailureRegistry.throwIfFailed(th);
        if (timeout > 0 && System.currentTimeMillis() >= deadline) {
          throw new UnrecoverableException(
              "Health-check at "
                  + path
                  + " did not become ready within "
                  + timeout
                  + "ms. Configure '"
                  + DevServerSettings.LiveReloadStartupTimeout
                  + "' to adjust the timeout (0 disables it).");
        }
        logger.debug("Waiting for the health-check to return success ...");
        var healthResponse =
            isHealthy(logger, path, settings.getHttpHost(), settings.getHttpPort());
        if (healthResponse == 1) {
          // success
          return;
        } else if (healthResponse == 0) {
          // non-success response, but not an exception
          Thread.sleep(50L);
        } else if (healthResponse == -1) {
          // connection exception
          Thread.sleep(50L);
        } else if (healthResponse == 404) {
          // health-check isn't implemented?
          throw new UnrecoverableException(
              "Health-check route " + path + " responded with 404. Is it implemented?");
        }
      }
    } catch (InterruptedException e) {
      // Don't print anything, just quit
    }
  }
}
