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
        logger.debug("Waiting thread to finish");
        try {
            th.join();
        } catch (InterruptedException ex) {
            logger.error("Interrupted during join", ex);
        }
    }

}
