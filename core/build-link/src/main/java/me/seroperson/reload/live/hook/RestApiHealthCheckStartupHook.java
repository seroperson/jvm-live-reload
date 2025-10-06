package me.seroperson.reload.live.hook;

import me.seroperson.reload.live.build.BuildLogger;

/**
 * Startup hook that waits for a REST API health check to succeed.
 *
 * <p>
 * This hook combines REST API health checking with startup waiting logic. It polls the server's
 * /health endpoint until it responds with HTTP 200, indicating the server has started successfully
 * and is ready to handle requests.
 */
public class RestApiHealthCheckStartupHook extends HealthCheckStartupHook
        implements RestApiHealthCheckHook {

}
