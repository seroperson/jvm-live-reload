package me.seroperson.reload.live.hook;

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
    var path = settings.getHealthCheckPath();
    long timeout = settings.getShutdownTimeoutMs();
    long deadline = System.currentTimeMillis() + timeout;
    try {
      while (true) {
        if (timeout > 0 && System.currentTimeMillis() >= deadline) {
          // The old generation is still answering well past the deadline (e.g. a framework worker
          // pool that keeps serving after main exits). Give up best-effort so teardown can finish
          // rather than holding the dev-server monitor forever. Do not throw: a shutdown hook that
          // throws would skip the remaining teardown steps.
          logger.warn(
              "Health-check at "
                  + path
                  + " was still answering "
                  + timeout
                  + "ms after shutdown began; giving up and continuing teardown. Configure '"
                  + DevServerSettings.LiveReloadShutdownTimeout
                  + "' to adjust the timeout (0 disables it).");
          return;
        }
        logger.debug("Waiting for the health-check to return failure ...");
        var healthResponse =
            isHealthy(logger, path, settings.getHttpHost(), settings.getHttpPort());
        if (healthResponse == 1) {
          // success - server still serving
          Thread.sleep(50L);
        } else if (healthResponse == 0) {
          // non-success response, but not an exception
          Thread.sleep(50L);
        } else if (healthResponse == -1) {
          // connection exception, that's what we're looking for
          return;
        } else if (healthResponse == 404) {
          // Health-check isn't implemented, so there is nothing to wait on. Give up best-effort
          // instead of throwing, which would skip the remaining teardown steps.
          logger.warn(
              "Health-check route "
                  + path
                  + " responded with 404; cannot confirm the old generation stopped. Continuing"
                  + " teardown.");
          return;
        }
      }
    } catch (InterruptedException e) {
      // Don't print anything, just quit
    }
  }
}
