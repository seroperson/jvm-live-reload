package me.seroperson.reload.live.hook;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import me.seroperson.reload.live.build.BuildLogger;

/**
 * Health check hook that uses REST API calls to determine server health.
 *
 * <p>
 * This hook performs health checks by making HTTP GET requests to a "/health" endpoint on the
 * server. The server is considered healthy if the endpoint returns a 200 status code within the
 * timeout period.
 */
interface RestApiHealthCheckHook extends HealthCheckHook {

  default boolean isHealthy(String path, String host, int port) {
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpRequest request =
          HttpRequest.newBuilder().uri(new URI("http://" + host + ":" + port + path)).GET()
              .timeout(java.time.Duration.ofMillis(500)).build();
      return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
    } catch (Exception e) {
      return false;
    }
  }

}
