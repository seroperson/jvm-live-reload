package me.seroperson.reload.live.hook

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.IdentityHashMap

/** Utility object for reflection-based operations in hook implementations.
  *
  * This object provides methods for checking class availability and managing
  * application shutdown hooks via reflection, which is useful for live reload
  * scenarios where normal shutdown procedures need to be controlled.
  */
object ReflectionUtils {

  /** Checks if a class with the given name is available on the classpath.
    *
    * @param className
    *   the fully qualified class name to check
    * @return
    *   true if the class is available, false otherwise
    */
  def hasClass(className: String): Boolean =
    try {
      Class.forName(className)
      true
    } catch {
      case e: ClassNotFoundException =>
        false
    }

  /** Runs all registered application shutdown hooks via reflection.
    *
    * This method uses reflection to access the internal
    * ApplicationShutdownHooks class and run all registered shutdown hooks.
    * After running the hooks, it resets the hooks field to prevent the
    * application from thinking it's permanently in shutdown state, which is
    * essential for live reload functionality.
    *
    * @note
    *   This method uses internal JVM APIs and may not work on all JVM
    *   implementations
    */
  def runApplicationShutdownHooks(): Unit = {
    try {
      val clazz = Class.forName("java.lang.ApplicationShutdownHooks")
      val method: Method = clazz.getDeclaredMethod("runHooks")
      method.setAccessible(true)
      method.invoke(null)

      // Reset the hooks field to a new IdentityHashMap to prevent
      // the application from thinking it's permanently in shutdown state
      val hooksField: Field = clazz.getDeclaredField("hooks")
      hooksField.setAccessible(true)
      hooksField.set(null, new IdentityHashMap[Thread, Thread]())
    } catch {
      case e: Exception =>
        System.err.println(
          s"Failed to run shutdown hooks via reflection: ${e.getMessage}"
        )
        e.printStackTrace();
    }
  }

}
