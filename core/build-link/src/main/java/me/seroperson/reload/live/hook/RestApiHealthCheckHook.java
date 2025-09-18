package me.seroperson.reload.live.hook;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import me.seroperson.reload.live.build.BuildLogger;

interface RestApiHealthCheckHook extends HealthCheckHook {

  default boolean isHealthy(String host, int port) {
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpRequest request =
          HttpRequest.newBuilder().uri(new URI("http://" + host + ":" + port + "/health")).GET()
              .timeout(java.time.Duration.ofMillis(500)).build();
      return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
    } catch (Exception e) {
      return false;
    }
  }

}
