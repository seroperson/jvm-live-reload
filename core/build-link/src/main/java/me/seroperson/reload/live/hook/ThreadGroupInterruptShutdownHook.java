package me.seroperson.reload.live.hook;

import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

public class ThreadGroupInterruptShutdownHook implements Hook {

  @Override
  public String description() {
    return "Interrupts the main application thread group";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public void hook(Thread th, ClassLoader cl, DevServerSettings settings, BuildLogger logger) {
    logger.debug("Application thread group active count: " + th.getThreadGroup().activeCount());
    th.getThreadGroup().interrupt();
  }

}
