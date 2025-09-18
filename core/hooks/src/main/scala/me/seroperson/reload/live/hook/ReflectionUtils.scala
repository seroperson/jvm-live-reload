package me.seroperson.reload.live.hook

object ReflectionUtils {

  def hasClass(className: String): Boolean =
    try {
      Class.forName(className)
      true
    } catch {
      case e: ClassNotFoundException =>
        false
    }

}
