package me.seroperson.reload.live.webserver;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import me.seroperson.reload.live.UnrecoverableException;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.build.ReloadableServer;

class ReloadHandler implements HttpHandler {

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
      logger.debug("Request successfully handled in ReloadHandler. Was reloaded: " + wasReloaded);
    } catch (UnrecoverableException e) {
      logger.error("Unrecoverable error during reloading", e);
      httpServerExchange.setStatusCode(503);
      httpServerExchange.getResponseSender().send("dev server stopped");
      try {
        server.close();
      } catch (Exception closeEx) {
        logger.error("Failed to close dev server after unrecoverable error", closeEx);
      }
    } catch (Exception e) {
      logger.error("Error during reloading", e);
      httpServerExchange.setStatusCode(500);
    }
  }
}
