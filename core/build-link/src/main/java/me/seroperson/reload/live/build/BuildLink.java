package me.seroperson.reload.live.build;

import java.io.Closeable;

/**
 * Interface used by the build plugin to communicate with an embedded server. BuildLink objects are
 * created by the plugin's run command and provided to embedded webserver.
 *
 * <p>This interface is written in Java and uses only Java types so that communication can work even
 * when the plugin and embedded webserver are built with different versions of Scala.
 */
public interface BuildLink extends Closeable {

  /**
   * Check if anything has changed, and if so, return an updated classloader.
   *
   * <p>This method is called multiple times on every request, so it is advised that change
   * detection happens asynchronously to this call, and that this call just check a boolean.
   *
   * @return Either
   *     <ul>
   *       <li>Throwable - If something went wrong (eg, a compile error).
   *       <li>ClassLoader - If the classloader has changed, and the application should be reloaded.
   *       <li>null - If nothing changed.
   *     </ul>
   */
  Object reload();

  /**
   * Signal that the next {@link #reload()} should produce a fresh classloader even if no source
   * files changed in the meantime. Used by the runner to retry a failed reload on the next request,
   * so a transient startup failure does not strand the dev server in a dead state.
   *
   * <p>The default implementation is a no-op for backwards compatibility with build-link
   * implementations that predate this method.
   */
  default void requestReload() {
    // no-op by default
  }
}
