package me.seroperson.reload.live.hook

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.IdentityHashMap

object ReflectionUtils {

  def hasClass(className: String): Boolean =
    try {
      Class.forName(className)
      true
    } catch {
      case e: ClassNotFoundException =>
        false
    }

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
