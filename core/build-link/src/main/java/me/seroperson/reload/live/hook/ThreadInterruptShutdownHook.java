package me.seroperson.reload.live.hook;

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
      // The application's main() did not honour the interrupt within the timeout
      // (blocked on a non-interruptible syscall, a busy-loop with no isInterrupted()
      // check, a runtime that swallows InterruptedException, etc.). Continue the
      // reload so the dev server doesn't hang forever; the leftover thread will keep
      // running with the previous classloader until it terminates on its own.
      logger.error(
          "⚠️ Application thread '"
              + th.getName()
              + "' did not exit within "
              + timeoutMs
              + "ms after interrupt; continuing reload. The previous generation's"
              + " classloader will not be eligible for GC until this thread terminates."
              + " Configure '"
              + DevServerSettings.LiveReloadThreadInterruptTimeout
              + "' to adjust the timeout.");
    }
  }
}
