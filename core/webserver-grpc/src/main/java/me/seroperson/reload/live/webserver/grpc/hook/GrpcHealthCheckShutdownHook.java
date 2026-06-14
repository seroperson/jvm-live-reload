package me.seroperson.reload.live.webserver.grpc.hook;

import me.seroperson.reload.live.UnrecoverableException;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Shutdown hook that waits for the GRPC health check to stop reporting SERVING.
 *
 * <p>Polls {@code grpc.health.v1.Health/Check} until the target server reports NOT_SERVING or
 * becomes unreachable, confirming the old generation is no longer answering before a new one is
 * started.
 */
public class GrpcHealthCheckShutdownHook extends GrpcHealthCheckHook {

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
    try {
      while (true) {
        logger.debug("Waiting for the GRPC health-check to stop returning SERVING ...");
        var response = isHealthy(logger, settings);
        logger.debug("Response from a health-check: " + response);
        if (response == 1) {
          Thread.sleep(50L);
        } else if (response == 0 || response == -1) {
          return;
        } else if (response == 404) {
          throw new UnrecoverableException(
              "GRPC health-check service '"
                  + settings.getGrpcHealthService()
                  + "' is not implemented by the target server.");
        }
      }
    } catch (InterruptedException e) {
      // Don't print anything, just quit
    } finally {
      closeChannel();
    }
  }
}
