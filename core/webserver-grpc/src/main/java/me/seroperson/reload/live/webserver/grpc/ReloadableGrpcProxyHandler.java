package me.seroperson.reload.live.webserver.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ChannelCredentials;
import io.grpc.ClientCall;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.TlsChannelCredentials;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import me.seroperson.reload.live.UnrecoverableException;
import me.seroperson.reload.live.build.BuildLogger;

/**
 * Manages a reloadable GRPC channel that can be refreshed when the application is reloaded.
 *
 * <p>This handler maintains a connection to the target GRPC server and provides the ability to
 * close and recreate the channel when the application is reloaded, ensuring that the proxy always
 * connects to the latest version of the application.
 */
class ReloadableGrpcProxyHandler {

  private final BuildLogger logger;
  private final GrpcDevServerStart server;
  private final String targetHost;
  private final int targetPort;
  private final boolean useTls;
  private final String trustPath;
  private final AtomicReference<ManagedChannel> channelRef = new AtomicReference<>();

  /**
   * Creates a new reloadable GRPC proxy handler.
   *
   * @param logger the logger for outputting messages
   * @param server the dev server for triggering reloads
   * @param targetHost the host of the target GRPC server
   * @param targetPort the port of the target GRPC server
   */
  public ReloadableGrpcProxyHandler(
      BuildLogger logger,
      GrpcDevServerStart server,
      String targetHost,
      int targetPort,
      boolean useTls,
      String trustPath) {
    this.logger = logger;
    this.server = server;
    this.targetHost = targetHost;
    this.targetPort = targetPort;
    this.useTls = useTls;
    this.trustPath = trustPath;
  }

  /**
   * Gets the current channel, creating one if necessary.
   *
   * @return the managed channel to the target server
   */
  public Channel getChannel() {
    ManagedChannel channel = channelRef.get();
    if (channel == null || channel.isShutdown() || channel.isTerminated()) {
      channel = createChannel();
      channelRef.set(channel);
    }
    return channel;
  }

  /**
   * Creates a new client call to the target server.
   *
   * @param methodDescriptor the method to call
   * @param callOptions the call options
   * @param <ReqT> the request type
   * @param <RespT> the response type
   * @return a new client call
   */
  public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
      MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {
    return getChannel().newCall(methodDescriptor, callOptions);
  }

  /** Refreshes the channel by closing the existing one and creating a new one. */
  public void refreshChannel() {
    closeChannel();
    logger.debug("Refreshing GRPC channel to " + targetHost + ":" + targetPort);
    channelRef.set(createChannel());
  }

  /** Closes the current channel if it exists. */
  public void closeChannel() {
    ManagedChannel channel = channelRef.getAndSet(null);
    if (channel != null && !channel.isShutdown()) {
      logger.debug("Closing GRPC channel");
      channel.shutdown();
      try {
        if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
          channel.shutdownNow();
        }
      } catch (InterruptedException e) {
        channel.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  public boolean reload() {
    return server.reload();
  }

  public void closeServer() throws IOException {
    server.close();
  }

  private ManagedChannel createChannel() {
    logger.debug(
        "Creating new GRPC channel to "
            + targetHost
            + ":"
            + targetPort
            + (useTls ? " (TLS)" : " (plaintext)"));
    ChannelCredentials credentials =
        useTls ? buildTlsCredentials() : InsecureChannelCredentials.create();
    return Grpc.newChannelBuilderForAddress(targetHost, targetPort, credentials).build();
  }

  private ChannelCredentials buildTlsCredentials() {
    if (trustPath == null || trustPath.isEmpty()) {
      return TlsChannelCredentials.create();
    }
    try {
      return TlsChannelCredentials.newBuilder().trustManager(new File(trustPath)).build();
    } catch (IOException e) {
      throw new UnrecoverableException(
          "Failed to read GRPC target TLS trust material from "
              + trustPath
              + ": "
              + e.getMessage());
    }
  }

  /**
   * Gets the target host.
   *
   * @return the target host
   */
  public String getTargetHost() {
    return targetHost;
  }

  /**
   * Gets the target port.
   *
   * @return the target port
   */
  public int getTargetPort() {
    return targetPort;
  }
}
