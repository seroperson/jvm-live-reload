package me.seroperson.reload.live.webserver.grpc;

import com.google.common.io.ByteStreams;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.HandlerRegistry;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;
import io.grpc.Status;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import me.seroperson.reload.live.UnrecoverableException;
import me.seroperson.reload.live.build.BuildLogger;

/**
 * A handler registry that proxies all GRPC calls to the target server.
 *
 * <p>This registry handles any method that is not explicitly defined, proxying the call to the
 * backend GRPC server. It uses a generic byte array marshaller to handle any protobuf message type.
 */
class GrpcProxyHandlerRegistry extends HandlerRegistry {

  private final BuildLogger logger;
  private final ReloadableGrpcProxyHandler proxyHandler;

  /**
   * Creates a new proxy handler registry.
   *
   * @param logger the logger for outputting messages
   * @param proxyHandler the proxy handler for forwarding calls
   */
  public GrpcProxyHandlerRegistry(BuildLogger logger, ReloadableGrpcProxyHandler proxyHandler) {
    this.logger = logger;
    this.proxyHandler = proxyHandler;
  }

  @Override
  public ServerMethodDefinition<?, ?> lookupMethod(String methodName, String authority) {
    logger.debug("Proxying method: " + methodName);

    try {
      proxyHandler.reload();
    } catch (UnrecoverableException e) {
      logger.error("Unrecoverable error during reloading", e);
      try {
        proxyHandler.closeServer();
      } catch (IOException ioe) {
        logger.error("Failed to close GRPC server after unrecoverable error", ioe);
      }
      throw e;
    }

    // Create a generic method descriptor for proxying
    MethodDescriptor<byte[], byte[]> proxyMethod =
        MethodDescriptor.<byte[], byte[]>newBuilder()
            .setType(MethodDescriptor.MethodType.UNKNOWN)
            .setFullMethodName(methodName)
            .setRequestMarshaller(new ByteArrayMarshaller())
            .setResponseMarshaller(new ByteArrayMarshaller())
            .build();

    return ServerMethodDefinition.create(proxyMethod, createProxyCallHandler(proxyMethod));
  }

  private ServerCallHandler<byte[], byte[]> createProxyCallHandler(
      MethodDescriptor<byte[], byte[]> methodDescriptor) {
    return (serverCall, metadata) -> {
      var clientCall = proxyHandler.newCall(serverCall.getMethodDescriptor(), CallOptions.DEFAULT);
      var proxyContainer =
          new ProxyContainer(metadata, serverCall.getMethodDescriptor(), clientCall, serverCall);
      clientCall.start(proxyContainer.clientCallListener, metadata);
      serverCall.request(1);
      clientCall.request(1);
      return proxyContainer.serverCallListener;
    };
  }

  private class ProxyContainer {
    private Metadata metadata;
    private MethodDescriptor<byte[], byte[]> methodDescriptor;
    private ClientCall<byte[], byte[]> clientCall;
    private ServerCall<byte[], byte[]> serverCall;
    private RequestProxy serverCallListener;
    private ResponseProxy clientCallListener;

    public ProxyContainer(
        Metadata metadata,
        MethodDescriptor<byte[], byte[]> methodDescriptor,
        ClientCall<byte[], byte[]> clientCall,
        ServerCall<byte[], byte[]> serverCall) {
      this.metadata = metadata;
      this.methodDescriptor = methodDescriptor;
      this.clientCall = clientCall;
      this.serverCall = serverCall;
      this.serverCallListener = new RequestProxy(this);
      this.clientCallListener = new ResponseProxy(this);
    }
  }

  private class RequestProxy extends ServerCall.Listener<byte[]> {
    private ProxyContainer proxyContainer;
    private boolean needToRequest = false;

    public RequestProxy(ProxyContainer proxyContainer) {
      this.proxyContainer = proxyContainer;
    }

    @Override
    public void onCancel() {
      proxyContainer.clientCall.cancel("Server cancelled", null);
    }

    @Override
    public void onHalfClose() {
      proxyContainer.clientCall.halfClose();
    }

    @Override
    public void onMessage(byte[] message) {
      proxyContainer.clientCall.sendMessage(message);
      synchronized (this) {
        if (proxyContainer.clientCall.isReady()) {
          proxyContainer.serverCall.request(1);
        } else {
          // The outgoing call is not ready for more requests. Stop requesting additional data and
          // wait for it to catch up.
          needToRequest = true;
        }
      }
    }

    @Override
    public void onReady() {
      proxyContainer.clientCallListener.onServerReady();
    }

    // Called from ResponseProxy, which is a different thread than the ServerCall.Listener
    // callbacks.
    synchronized void onClientReady() {
      if (needToRequest) {
        proxyContainer.serverCall.request(1);
        needToRequest = false;
      }
    }
  }

  private class ResponseProxy extends ClientCall.Listener<byte[]> {
    private final ProxyContainer proxyContainer;
    // Hold 'this' lock when accessing
    private boolean needToRequest;

    public ResponseProxy(ProxyContainer proxyContainer) {
      this.proxyContainer = proxyContainer;
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
      proxyContainer.serverCall.close(status, trailers);
    }

    @Override
    public void onHeaders(Metadata headers) {
      proxyContainer.serverCall.sendHeaders(headers);
    }

    @Override
    public void onMessage(byte[] message) {
      proxyContainer.serverCall.sendMessage(message);
      synchronized (this) {
        if (proxyContainer.serverCall.isReady()) {
          proxyContainer.clientCall.request(1);
        } else {
          // The incoming call is not ready for more responses. Stop requesting additional data
          // and wait for it to catch up.
          needToRequest = true;
        }
      }
    }

    @Override
    public void onReady() {
      proxyContainer.serverCallListener.onClientReady();
    }

    // Called from RequestProxy, which is a different thread than the ClientCall.Listener
    // callbacks.
    synchronized void onServerReady() {
      if (needToRequest) {
        proxyContainer.clientCall.request(1);
        needToRequest = false;
      }
    }
  }

  /** A simple marshaller that passes through byte arrays without transformation. */
  private static class ByteArrayMarshaller implements MethodDescriptor.Marshaller<byte[]> {

    @Override
    public InputStream stream(byte[] value) {
      return new ByteArrayInputStream(value);
    }

    @Override
    public byte[] parse(InputStream stream) {
      try {
        return ByteStreams.toByteArray(stream);
      } catch (IOException e) {
        throw new RuntimeException("Failed to read bytes from stream", e);
      }
    }
  }
}
