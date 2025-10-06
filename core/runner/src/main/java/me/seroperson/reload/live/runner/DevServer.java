package me.seroperson.reload.live.runner;

import java.io.Closeable;

import me.seroperson.reload.live.build.BuildLink;

/**
 * Interface representing a development server with live reload capabilities.
 *
 * <p>The development server provides access to the build link for compilation
 * and reload operations, and can trigger reloads when source code changes.
 * It implements Closeable to ensure proper resource cleanup when the server
 * is shut down.
 */
public interface DevServer extends Closeable {

    /**
     * Gets the build link associated with this development server.
     *
     * @return the BuildLink instance for compilation and reload operations
     */
    BuildLink buildLink();

    /**
     * Triggers a reload of the application if changes are detected.
     *
     * @return true if the application was reloaded, false if no reload was necessary
     */
    boolean reload();

}
