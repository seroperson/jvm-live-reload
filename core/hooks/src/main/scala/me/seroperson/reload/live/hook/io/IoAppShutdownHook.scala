package me.seroperson.reload.live.hook.io

/** Convenience alias for the Cats Effect IOApp shutdown hook.
  *
  * This class provides a more conventional package location for the
  * IoAppEffectShutdownHook while maintaining the same functionality. It's used
  * to maintain API compatibility and provide a cleaner import path for users of
  * the live reload system.
  */
class IoAppShutdownHook extends cats.effect.IoAppEffectShutdownHook
