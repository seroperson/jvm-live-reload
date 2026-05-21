package me.seroperson.reload.live.webserver;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.proxy.ProxyCallback;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyConnection;
import io.undertow.util.AttachmentKey;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.Channel;
import java.util.concurrent.TimeUnit;
import me.seroperson.reload.live.build.BuildLogger;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;

/**
 * Initially it was the SimpleProxyClientProvider from undertow library, but we had to tweak it a
 * little to support reconnection after reload.
 */
class ReloadableProxyClient implements ProxyClient {

  private final URI uri;
  private final AttachmentKey<ClientConnection> clientAttachmentKey =
      AttachmentKey.create(ClientConnection.class);
  private final UndertowClient client;
  private final BuildLogger logger;
  private volatile XnioWorker currentGenerationWorker;

  private static final ProxyTarget TARGET = new ProxyTarget() {};

  public ReloadableProxyClient(BuildLogger logger, URI uri) {
    this.uri = uri;
    this.logger = logger;
    client = UndertowClient.getInstance();
  }

  @Override
  public ProxyTarget findTarget(HttpServerExchange exchange) {
    return TARGET;
  }

  public void setCurrentGenerationWorker(XnioWorker worker) {
    this.currentGenerationWorker = worker;
  }

  @Override
  public void getConnection(
      ProxyTarget target,
      HttpServerExchange exchange,
      ProxyCallback<ProxyConnection> callback,
      long timeout,
      TimeUnit timeUnit) {
    ServerConnection serverConnection = exchange.getConnection();
    ClientConnection existing = serverConnection.getAttachment(clientAttachmentKey);
    if (existing != null) {
      logger.debug("Connection already exists");
      if (existing.isOpen()) {
        var wasReloaded = exchange.getAttachment(ReloadHandler.WAS_RELOADED);
        logger.debug("Reusing opened proxy connection. Was reloaded: " + wasReloaded);
        if (wasReloaded != null && wasReloaded) {
          logger.debug("Closing existing proxy connection after reloading");

          existing.getCloseSetter().set(null);
          IoUtils.safeClose(existing);
          serverConnection.removeAttachment(clientAttachmentKey);
          exchange.removeAttachment(ReloadHandler.WAS_RELOADED);

          client.connect(
              new ConnectNotifier(callback, exchange),
              uri,
              currentGenerationWorker,
              serverConnection.getByteBufferPool(),
              OptionMap.EMPTY);
        } else {
          // this connection already has a client, re-use it
          callback.completed(
              exchange, new ProxyConnection(existing, uri.getPath() == null ? "/" : uri.getPath()));
        }
        return;
      } else {
        serverConnection.removeAttachment(clientAttachmentKey);
      }
    }
    logger.debug("Creating a new proxy connection for path " + exchange.getRequestPath());
    client.connect(
        new ConnectNotifier(callback, exchange),
        uri,
        currentGenerationWorker,
        serverConnection.getByteBufferPool(),
        OptionMap.EMPTY);
  }

  private final class ConnectNotifier implements ClientCallback<ClientConnection> {
    private final ProxyCallback<ProxyConnection> callback;
    private final HttpServerExchange exchange;

    private ConnectNotifier(ProxyCallback<ProxyConnection> callback, HttpServerExchange exchange) {
      this.callback = callback;
      this.exchange = exchange;
    }

    @Override
    public void completed(final ClientConnection connection) {
      final ServerConnection serverConnection = exchange.getConnection();
      serverConnection.putAttachment(clientAttachmentKey, connection);
      serverConnection.addCloseListener(
          serverConnection1 -> {
            IoUtils.safeClose(connection);
            logger.debug("Closing server proxy connection for path " + exchange.getRequestPath());
          });
      connection
          .getCloseSetter()
          .set(
              (ChannelListener<Channel>)
                  channel -> {
                    serverConnection.removeAttachment(clientAttachmentKey);
                    logger.debug(
                        "Closing client proxy connection for path " + exchange.getRequestPath());
                  });
      var path = uri.getPath() == null ? "/" : uri.getPath();
      callback.completed(exchange, new ProxyConnection(connection, path));
    }

    @Override
    public void failed(IOException e) {
      callback.failed(exchange);
      logger.error("Error during connection", e);
    }
  }
}
