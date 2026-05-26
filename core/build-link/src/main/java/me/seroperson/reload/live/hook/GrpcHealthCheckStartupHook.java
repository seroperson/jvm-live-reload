package me.seroperson.reload.live.hook;

import me.seroperson.reload.live.UnrecoverableException;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Startup hook that waits for a GRPC health check to succeed.
 *
 * <p>This hook combines GRPC health checking with startup waiting logic. It polls the GRPC server
 * until it responds, indicating the server has started successfully and is ready to handle
 * requests.
 */
public class GrpcHealthCheckStartupHook implements GrpcHealthCheckHook {

  @Override
  public String description() {
    return "Waits for GRPC health-check to return true";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public void hook(Thread th, ClassLoader cl, DevServerSettings settings, BuildLogger logger) {
    var service = settings.getGrpcHealthService();
    long timeout = settings.getStartupTimeoutMs();
    long deadline = System.currentTimeMillis() + timeout;
    try {
      while (true) {
        AppFailureRegistry.throwIfFailed(th);
        if (timeout > 0 && System.currentTimeMillis() >= deadline) {
          throw new UnrecoverableException(
              "GRPC health-check service "
                  + service
                  + " did not become available within "
                  + timeout
                  + "ms. Configure '"
                  + DevServerSettings.LiveReloadStartupTimeout
                  + "' to adjust the timeout (0 disables it).");
        }
        logger.debug("Waiting for the GRPC health-check to return success ...");
        var healthResponse =
            isHealthy(logger, service, settings.getGrpcHost(), settings.getGrpcPort());
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
          // health-check service isn't implemented
          throw new UnrecoverableException(
              "GRPC health-check service " + service + " is not available. Is it implemented?");
        }
      }
    } catch (InterruptedException e) {
      // Don't print anything, just quit
    }
  }
}
