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
import me.seroperson.reload.live.UnrecoverableException;
import me.seroperson.reload.live.build.BuildLogger;

/**
 * Manages a reloadable GRPC channel that can be refreshed when the application is reloaded.
 *
 * <p>This handler maintains a connection to the target GRPC server and provides the ability to
 * close and recreate the channel when the application is reloaded, ensuring that the proxy always
 * connects to the latest version of the application.
 *
 * <p>Channel lifecycle is coordinated through {@code channelLock}: the lock is held only across the
 * brief field swap (and the lazy create in {@link #getChannel()}). The actual {@code
 * shutdown}/{@code awaitTermination} is performed <em>outside</em> the lock so that a slow shutdown
 * of the old channel does not stall concurrent reads.
 */
class ReloadableGrpcProxyHandler {

  private final BuildLogger logger;
  private final GrpcDevServerStart server;
  private final String targetHost;
  private final int targetPort;
  private final boolean useTls;
  private final String trustPath;

  // Guards every assignment to `channel` and the lazy-create in getChannel(). Reads on the
  // fast path are unsynchronized (volatile) for performance; correctness is preserved by
  // never letting the field be momentarily null between a close and a replace.
  private final Object channelLock = new Object();
  private volatile ManagedChannel channel;

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
    // Fast path: volatile read. If the channel is live, return it without locking.
    ManagedChannel current = channel;
    if (current != null && !current.isShutdown() && !current.isTerminated()) {
      return current;
    }
    // Slow path: lazily create exactly one channel under the lock. Double-checked so that
    // concurrent callers don't each build their own ManagedChannel (the previous
    // AtomicReference + plain set() pattern leaked the loser of that race).
    synchronized (channelLock) {
      current = channel;
      if (current == null || current.isShutdown() || current.isTerminated()) {
        current = createChannel();
        channel = current;
      }
      return current;
    }
  }

  /**
   * Creates a new client call to the target server.
   *
   * <p>There is a small inherent window where the channel can be shut down by a concurrent {@link
   * #refreshChannel()} between {@link #getChannel()} and the eventual {@code start()} on the
   * returned call, in which case gRPC will surface {@code Status.UNAVAILABLE: Channel shutdown
   * invoked}. This is the best we can do without ref-counting in-flight calls; external callers
   * should rely on gRPC's own retry policy for transient failures around a reload boundary.
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

  /** Refreshes the channel by installing a new one and shutting down the previous one. */
  public void refreshChannel() {
    ManagedChannel previous;
    ManagedChannel replacement;
    synchronized (channelLock) {
      previous = channel;
      // Build the replacement and publish it before the previous channel is shut down so
      // concurrent getChannel() callers waiting on the lock never see a momentary null.
      logger.debug("Refreshing GRPC channel to " + targetHost + ":" + targetPort);
      replacement = createChannel();
      channel = replacement;
    }
    // Shutdown is performed outside the lock: awaitTermination can block for up to 5s and
    // there is no reason to make new readers wait for the old channel to drain.
    if (previous != null) {
      shutdownChannel(previous);
    }
  }

  /** Closes the current channel if it exists. */
  public void closeChannel() {
    ManagedChannel previous;
    synchronized (channelLock) {
      previous = channel;
      channel = null;
    }
    if (previous != null) {
      shutdownChannel(previous);
    }
  }

  /**
   * Gracefully shuts down a single channel, escalating to {@code shutdownNow()} if it does not
   * terminate within 5 seconds. Idempotent — safe to call on an already-shutdown channel.
   */
  private void shutdownChannel(ManagedChannel ch) {
    if (ch.isShutdown()) {
      return;
    }
    logger.debug("Closing GRPC channel");
    ch.shutdown();
    try {
      if (!ch.awaitTermination(5, TimeUnit.SECONDS)) {
        ch.shutdownNow();
      }
    } catch (InterruptedException e) {
      ch.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  public boolean reload() {
    return server.reload();
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
