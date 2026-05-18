package me.seroperson.reload.live.hook;

import me.seroperson.reload.live.UnrecoverableException;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

public class ThreadInterruptShutdownHook implements Hook {

  @Override
  public String description() {
    return "Interrupts the main application thread";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public void hook(Thread th, ClassLoader cl, DevServerSettings settings, BuildLogger logger) {
    th.interrupt();
    long timeoutMs = settings.getThreadInterruptTimeoutMs();
    logger.debug("Waiting up to " + timeoutMs + "ms for thread to finish");
    try {
      th.join(timeoutMs);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      logger.error("Interrupted during join", ex);
      return;
    }
    if (th.isAlive()) {
      // main() ignored the interrupt.
      throw new UnrecoverableException(
          "Application thread '"
              + th.getName()
              + "' did not exit within "
              + timeoutMs
              + "ms after interrupt. Configure '"
              + DevServerSettings.LiveReloadThreadInterruptTimeout
              + "' to adjust the timeout.");
    }
  }
}
