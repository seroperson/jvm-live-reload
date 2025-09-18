package me.seroperson.reload.live.webserver;

import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.build.ReloadableServer;
import me.seroperson.reload.live.build.BuildLink;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HandlerWrapper;
import io.undertow.util.AttachmentKey;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;

public class ReloadHandler implements HttpHandler {

  public static final AttachmentKey<Boolean> WAS_RELOADED = AttachmentKey.create(Boolean.class);

  private final ReloadableServer server;
  private final HttpHandler next;
  private final BuildLogger logger;

  public ReloadHandler(BuildLogger logger, ReloadableServer server, HttpHandler next) {
    this.logger = logger;
    this.server = server;
    this.next = next;
  }

  @Override
  public void handleRequest(HttpServerExchange httpServerExchange) throws Exception {
    try {
      var wasReloaded = server.reload();

      httpServerExchange.setRelativePath(httpServerExchange.getRequestPath());
      httpServerExchange.putAttachment(WAS_RELOADED, wasReloaded);

      next.handleRequest(httpServerExchange);
    } catch (Throwable e) {
      logger.error("Error during reloading", e);
    }
  }

}
