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

  /**
   * A {@code 404} during application warmup is expected: most JVM web frameworks bind the listen
   * socket before registering their routes, so the configured health route transiently returns
   * {@code 404} until startup completes. Only a {@code 404} that persists beyond this grace window
   * is treated as a genuinely missing/misconfigured health route.
   */
  private static final long NOT_IMPLEMENTED_GRACE_MS = 5000L;

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
      // Deadline after which a still-ongoing 404 is treated as "not implemented".
      // -1 means no 404 has been observed yet (or the streak was broken).
      long notImplementedDeadline = -1L;
      while (true) {
        AppFailureRegistry.throwIfFailed(th);
        logger.debug("Waiting for the health-check to return success ...");
        var path = settings.getHealthCheckPath();
        var healthResponse =
            isHealthy(logger, path, settings.getHttpHost(), settings.getHttpPort());
        if (healthResponse == 1) {
          // success
          return;
        } else if (healthResponse == 404) {
          // The server is reachable but the health route may not be registered yet during
          // warmup. Keep polling, and only give up once 404 persists past the grace window.
          long now = System.currentTimeMillis();
          if (notImplementedDeadline < 0L) {
            notImplementedDeadline = now + NOT_IMPLEMENTED_GRACE_MS;
          } else if (now >= notImplementedDeadline) {
            throw new UnrecoverableException(
                "Health-check route "
                    + path
                    + " kept responding with 404 for over "
                    + NOT_IMPLEMENTED_GRACE_MS
                    + "ms. Is it implemented?");
          }
          Thread.sleep(50L);
        } else {
          // healthResponse == 0 (non-success response) or -1 (connection exception): the app is
          // still coming up. Reset the 404 grace window and keep polling.
          notImplementedDeadline = -1L;
          Thread.sleep(50L);
        }
      }
    } catch (InterruptedException e) {
      // Don't print anything, just quit
    }
  }
}
