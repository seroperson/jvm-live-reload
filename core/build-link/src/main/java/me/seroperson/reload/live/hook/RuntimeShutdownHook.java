package me.seroperson.reload.live.hook;

import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Generic runtime shutdown hook that runs application shutdown hooks.
 *
 * <p>This hook is designed to gracefully shut down applications by running all registered shutdown
 * hooks. It's a fallback option that works with any Java application regardless of the specific
 * framework used.
 */
public class RuntimeShutdownHook implements Hook {

  @Override
  public String description() {
    return "Shutdown a generic application";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public void hook(Thread th, ClassLoader cl, DevServerSettings settings, BuildLogger logger) {
    ReflectionUtils.runApplicationShutdownHooks(logger);
  }
}
