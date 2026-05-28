package me.seroperson.reload.live.webserver.grpc.hook;

import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Shutdown hook that waits for the GRPC health check to stop reporting SERVING.
 *
 * <p>Polls {@code grpc.health.v1.Health/Check} until the target server reports NOT_SERVING or
 * becomes unreachable, confirming the old generation is no longer answering before a new one is
 * started.
 */
public class GrpcHealthCheckShutdownHook implements GrpcHealthCheckHook {

  @Override
  public String description() {
    return "Waits for GRPC health-check to stop reporting SERVING";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public void hook(Thread th, ClassLoader cl, DevServerSettings settings, BuildLogger logger) {
    long timeout = settings.getShutdownTimeoutMs();
    long deadline = System.currentTimeMillis() + timeout;
    try {
      while (true) {
        if (timeout > 0 && System.currentTimeMillis() >= deadline) {
          // The old generation is still reporting SERVING well past the deadline. Give up
          // best-effort so teardown can finish rather than holding the dev-server monitor forever.
          // Do not throw: a shutdown hook that throws would skip the remaining teardown steps.
          logger.warn(
              "GRPC health-check service '"
                  + settings.getGrpcHealthService()
                  + "' was still reporting SERVING "
                  + timeout
                  + "ms after shutdown began; giving up and continuing teardown. Configure '"
                  + DevServerSettings.LiveReloadShutdownTimeout
                  + "' to adjust the timeout (0 disables it).");
          return;
        }
        logger.debug("Waiting for the GRPC health-check to stop returning SERVING ...");
        var response = isHealthy(logger, settings);
        logger.debug("Response from a health-check: " + response);
        if (response == 1) {
          Thread.sleep(50L);
        } else if (response == 0 || response == -1) {
          return;
        } else if (response == 404) {
          // Health-check service isn't implemented, so there is nothing to wait on. Give up
          // best-effort instead of throwing, which would skip the remaining teardown steps.
          logger.warn(
              "GRPC health-check service '"
                  + settings.getGrpcHealthService()
                  + "' is not implemented by the target server; cannot confirm the old generation"
                  + " stopped. Continuing teardown.");
          return;
        }
      }
    } catch (InterruptedException e) {
      // Don't print anything, just quit
    }
  }
}
