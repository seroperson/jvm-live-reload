package me.seroperson.reload.live.webserver.grpc.hook;

import me.seroperson.reload.live.UnrecoverableException;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.hook.AppFailureRegistry;
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Startup hook that waits for the GRPC health check to report SERVING.
 *
 * <p>Polls {@code grpc.health.v1.Health/Check} on the target server until the configured service
 * reports {@code SERVING}, ensuring the new generation of the application is ready before requests
 * are accepted.
 */
public class GrpcHealthCheckStartupHook implements GrpcHealthCheckHook {

  @Override
  public String description() {
    return "Waits for GRPC health-check to report SERVING";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public void hook(Thread th, ClassLoader cl, DevServerSettings settings, BuildLogger logger) {
    long timeout = settings.getStartupTimeoutMs();
    long deadline = System.currentTimeMillis() + timeout;
    try {
      while (true) {
        AppFailureRegistry.throwIfFailed(th);
        if (timeout > 0 && System.currentTimeMillis() >= deadline) {
          throw new UnrecoverableException(
              "GRPC health-check service '"
                  + settings.getGrpcHealthService()
                  + "' did not report SERVING within "
                  + timeout
                  + "ms. Configure '"
                  + DevServerSettings.LiveReloadStartupTimeout
                  + "' to adjust the timeout (0 disables it).");
        }
        logger.debug("Waiting for the GRPC health-check to return SERVING ...");
        var response = isHealthy(logger, settings);
        if (response == 1) {
          return;
        } else if (response == 0 || response == -1) {
          Thread.sleep(50L);
        } else if (response == 404) {
          throw new UnrecoverableException(
              "GRPC health-check service '"
                  + settings.getGrpcHealthService()
                  + "' is not implemented by the target server. Register"
                  + " io.grpc.protobuf.services.HealthStatusManager (grpc-services) or override the"
                  + " '"
                  + DevServerSettings.LiveReloadGrpcHealthService
                  + "' setting.");
        }
      }
    } catch (InterruptedException e) {
      // Don't print anything, just quit
    }
  }
}
