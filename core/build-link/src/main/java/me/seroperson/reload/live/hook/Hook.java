package me.seroperson.reload.live.hook;

import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Interface for hooks that can be executed during application lifecycle events.
 *
 * <p>
 * Hooks provide a way to execute custom logic at specific points during application startup or
 * shutdown. Each hook can check if it's available (e.g., if required dependencies are present) and
 * provide a description of what it does.
 */
public interface Hook {

    /**
     * Returns a human-readable description of what this hook does.
     *
     * @return a description of the hook's functionality
     */
    String description();

    /**
     * Checks if this hook is available and can be executed.
     *
     * <p>
     * This method is typically used to check if required dependencies or conditions are met before
     * attempting to execute the hook.
     *
     * @return true if the hook is available and can be executed, false otherwise
     */
    boolean isAvailable();

    /**
     * Executes the hook with the provided settings and logger.
     *
     * @param th       main application thread
     * @param cl       reloaded classloader
     * @param settings the development server settings
     * @param logger   the build logger for outputting messages
     */
    void hook(Thread th, ClassLoader cl, DevServerSettings settings, BuildLogger logger);

}
