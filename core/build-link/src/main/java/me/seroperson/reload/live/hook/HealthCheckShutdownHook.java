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

  /**
   * During teardown the application may answer {@code 404} (routes already deregistered) while its
   * listen socket is still open. Such a {@code 404} is transient and must not abort the reload;
   * only a {@code 404} that persists beyond this grace window means the health route was never
   * implemented.
   */
  private static final long NOT_IMPLEMENTED_GRACE_MS = 5000L;

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
      // Deadline after which a still-ongoing 404 is treated as "not implemented".
      // -1 means no 404 has been observed yet (or the streak was broken).
      long notImplementedDeadline = -1L;
      while (true) {
        logger.debug("Waiting for the health-check to return failure ...");
        var path = settings.getHealthCheckPath();
        var healthResponse =
            isHealthy(logger, path, settings.getHttpHost(), settings.getHttpPort());
        if (healthResponse == -1) {
          // connection exception, that's what we're looking for
          return;
        } else if (healthResponse == 404) {
          // The old generation is still serving on its socket but may have deregistered routes
          // mid-teardown. Keep polling until the connection is actually refused; only give up if
          // 404 persists past the grace window (health route genuinely not implemented).
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
          // healthResponse == 1 (still healthy) or 0 (reachable, non-success): the old generation
          // is still up. Reset the 404 grace window and keep polling.
          notImplementedDeadline = -1L;
          Thread.sleep(50L);
        }
      }
    } catch (InterruptedException e) {
      // Don't print anything, just quit
    }
  }
}
