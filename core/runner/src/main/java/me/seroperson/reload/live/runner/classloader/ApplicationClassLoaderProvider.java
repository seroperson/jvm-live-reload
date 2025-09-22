package me.seroperson.reload.live.runner.classloader;

import java.net.URLClassLoader;

/**
 * Provider interface for accessing the current application class loader.
 *
 * <p>
 * This interface is used to decouple components that need access to the application class loader
 * from the actual class loader instance. It allows for lazy access and enables the class loader to
 * be changed during runtime (e.g., during live reload operations).
 */
public interface ApplicationClassLoaderProvider {

  /**
   * Gets the current application class loader.
   *
   * @return the current application class loader, or null if none is available
   */
  URLClassLoader get();

}
