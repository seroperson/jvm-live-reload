package me.seroperson.reload.live.hook;

import me.seroperson.reload.live.build.BuildLogger;

/**
 * Shutdown hook that waits for a REST API health check to fail.
 *
 * <p>
 * This hook combines REST API health checking with shutdown waiting logic. It polls the server's
 * /health endpoint until it stops responding with HTTP 200, indicating the server has shut down
 * successfully.
 */
public class RestApiHealthCheckShutdownHook extends HealthCheckShutdownHook
        implements RestApiHealthCheckHook {

}
