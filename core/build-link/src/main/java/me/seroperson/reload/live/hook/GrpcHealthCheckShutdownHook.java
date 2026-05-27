package me.seroperson.reload.live.hook;

import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Shutdown hook that waits for a GRPC health check to fail.
 *
 * <p>This hook combines GRPC health checking with shutdown waiting logic. It polls the GRPC server
 * until it stops responding, indicating the server has shut down successfully.
 */
public class GrpcHealthCheckShutdownHook implements GrpcHealthCheckHook {

  @Override
  public String description() {
    return "Waits for GRPC health-check to return false";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public void hook(Thread th, ClassLoader cl, DevServerSettings settings, BuildLogger logger) {
    var service = settings.getGrpcHealthService();
    long timeout = settings.getShutdownTimeoutMs();
    long deadline = System.currentTimeMillis() + timeout;
    try {
      while (true) {
        if (timeout > 0 && System.currentTimeMillis() >= deadline) {
          // The old generation is still answering well past the deadline. Give up best-effort so
          // teardown can finish rather than holding the dev-server monitor forever. Do not throw: a
          // shutdown hook that throws would skip the remaining teardown steps.
          logger.warn(
              "GRPC health-check service "
                  + service
                  + " was still answering "
                  + timeout
                  + "ms after shutdown began; giving up and continuing teardown. Configure '"
                  + DevServerSettings.LiveReloadShutdownTimeout
                  + "' to adjust the timeout (0 disables it).");
          return;
        }
        logger.debug("Waiting for the GRPC health-check to return failure ...");
        var healthResponse =
            isHealthy(logger, service, settings.getGrpcHost(), settings.getGrpcPort());
        if (healthResponse == 1) {
          // success - server still running
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
              "GRPC health-check service "
                  + service
                  + " is not available; cannot confirm the old generation stopped. Continuing"
                  + " teardown.");
          return;
        }
      }
    } catch (InterruptedException e) {
      // Don't print anything, just quit
    }
  }
}
