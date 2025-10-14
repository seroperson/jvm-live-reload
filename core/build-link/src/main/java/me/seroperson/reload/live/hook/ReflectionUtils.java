package me.seroperson.reload.live.hook;

import java.util.IdentityHashMap;
import me.seroperson.reload.live.build.BuildLogger;

/**
 * Utility object for reflection-based operations in hook implementations.
 *
 * <p>This object provides methods for checking class availability and managing application shutdown
 * hooks via reflection, which is useful for live reload scenarios where normal shutdown procedures
 * need to be controlled.
 */
public class ReflectionUtils {

  /**
   * Checks if a class with the given name is available on the classpath.
   *
   * @param className the fully qualified class name to check
   * @return true if the class is available, false otherwise
   */
  public static boolean hasClass(String className) {
    try {
      Class.forName(className);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /**
   * Runs all registered application shutdown hooks via reflection.
   *
   * <p>This method uses reflection to access the internal ApplicationShutdownHooks class and run
   * all registered shutdown hooks. After running the hooks, it resets the hooks field to prevent
   * the application from thinking it's permanently in shutdown state, which is essential for live
   * reload functionality.
   *
   * <p>This method uses internal JVM APIs and may not work on all JVM implementations.
   */
  public static void runApplicationShutdownHooks(BuildLogger logger) {
    try {
      logger.debug("Running shutdown hooks");
      var clazz = Class.forName("java.lang.ApplicationShutdownHooks");
      var method = clazz.getDeclaredMethod("runHooks");
      method.setAccessible(true);
      method.invoke(null);
      logger.debug("java.lang.ApplicationShutdownHooks.runHooks was invoked successfully");

      // Reset the hooks field to a new IdentityHashMap to prevent
      // the application from thinking it's permanently in shutdown state
      var hooksField = clazz.getDeclaredField("hooks");
      hooksField.setAccessible(true);
      hooksField.set(null, new IdentityHashMap<Thread, Thread>());
      logger.debug("java.lang.ApplicationShutdownHooks.hooks were invalidated successfully");
    } catch (Exception e) {
      logger.error("Failed to run shutdown hooks via reflection", e);
    }
  }
}
