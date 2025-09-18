package me.seroperson.reload.live.webserver;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyCallback;
import io.undertow.server.handlers.proxy.ProxyConnection;
import io.undertow.util.AttachmentKey;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.Channel;
import java.util.concurrent.TimeUnit;

/**
 * Initially it was the SimpleProxyClientProvider, but we had to tweak it a little to support
 * reconnection after reload.
 */
public class ReloadableProxyClient implements ProxyClient {

  private final URI uri;
  private final AttachmentKey<ClientConnection> clientAttachmentKey =
      AttachmentKey.create(ClientConnection.class);
  private final UndertowClient client;

  private static final ProxyTarget TARGET = new ProxyTarget() {};

  public ReloadableProxyClient(URI uri) {
    this.uri = uri;
    client = UndertowClient.getInstance();
  }

  @Override
  public ProxyTarget findTarget(HttpServerExchange exchange) {
    return TARGET;
  }

  @Override
  public void getConnection(ProxyTarget target, HttpServerExchange exchange,
      ProxyCallback<ProxyConnection> callback, long timeout, TimeUnit timeUnit) {
    ClientConnection existing = exchange.getConnection().getAttachment(clientAttachmentKey);
    if (existing != null) {
      if (existing.isOpen()) {
        var wasReloaded = exchange.getAttachment(ReloadHandler.WAS_RELOADED);
        System.out.println("Inside of ReloadableProxy. wasReloaded: " + wasReloaded);
        if (wasReloaded != null && wasReloaded) {
          System.out.println("Inside of ReloadableProxy. Closing existing connection");
          IoUtils.safeClose(existing);
          client.connect(new ConnectNotifier(callback, exchange), uri, exchange.getIoThread(),
              exchange.getConnection().getByteBufferPool(), OptionMap.EMPTY);
          exchange.removeAttachment(ReloadHandler.WAS_RELOADED);
          System.out.println(
              "Inside of ReloadableProxy. Created new connection and removed was_reloaded flag");
        } else {
          // this connection already has a client, re-use it
          callback.completed(exchange,
              new ProxyConnection(existing, uri.getPath() == null ? "/" : uri.getPath()));
        }
        return;
      } else {
        exchange.getConnection().removeAttachment(clientAttachmentKey);
      }
    }
    client.connect(new ConnectNotifier(callback, exchange), uri, exchange.getIoThread(),
        exchange.getConnection().getByteBufferPool(), OptionMap.EMPTY);
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
      serverConnection.addCloseListener(new ServerConnection.CloseListener() {
        @Override
        public void closed(ServerConnection serverConnection) {
          IoUtils.safeClose(connection);
        }
      });
      connection.getCloseSetter().set(new ChannelListener<Channel>() {
        @Override
        public void handleEvent(Channel channel) {
          serverConnection.removeAttachment(clientAttachmentKey);
        }
      });
      callback.completed(exchange,
          new ProxyConnection(connection, uri.getPath() == null ? "/" : uri.getPath()));
    }

    @Override
    public void failed(IOException e) {
      callback.failed(exchange);
    }
  }


}

