package me.seroperson.reload.live.hook;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

interface HealthCheckHook extends Hook {

  boolean isHealthy(String host, int port);

}
