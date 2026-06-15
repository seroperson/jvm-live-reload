package me.seroperson.reload.live.webserver.grpc.hook;

import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import me.seroperson.reload.live.UnrecoverableException;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.hook.Hook;
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Base interface for hooks that probe the GRPC health checking protocol of the target server.
 *
 * <p>A single {@link ManagedChannel} is opened once via {@link #openChannel} for the duration of a
 * polling loop and reused across every {@link #checkHealth} probe, then released with {@link
 * #closeChannel}. A channel is a heavyweight, long-lived object (name resolution, connection pool,
 * backoff), so it must not be recreated per poll. The {@code service} field of the request can be
 * configured via {@link DevServerSettings#getGrpcHealthService()}; an empty string queries the
 * overall server health as specified by the protocol.
 */
interface GrpcHealthCheckHook extends Hook {

  /**
   * Opens a channel to the target GRPC server. Reuse the returned channel for every {@link
   * #checkHealth} call in a polling loop and release it with {@link #closeChannel} when done.
   *
   * @param settings dev server settings (host, port, TLS flag)
   * @return a new managed channel to the target server
   */
  default ManagedChannel openChannel(DevServerSettings settings) {
    var host = settings.getGrpcHost();
    var port = settings.getGrpcPort();
    ChannelCredentials credentials =
        settings.isGrpcTargetTls()
            ? buildTlsCredentials(settings.getGrpcTargetTlsTrust())
            : InsecureChannelCredentials.create();
    return Grpc.newChannelBuilderForAddress(host, port, credentials).build();
  }

  /**
   * Probes the target GRPC server for health over an already-open channel.
   *
   * @param channel the channel to probe over (see {@link #openChannel})
   * @param logger build logger
   * @param settings dev server settings (service name)
   * @return 1 SERVING, 0 NOT_SERVING/UNKNOWN, -1 connection error, 404 health service not
   *     implemented by the target
   */
  default int checkHealth(ManagedChannel channel, BuildLogger logger, DevServerSettings settings) {
    var service = settings.getGrpcHealthService();
    try {
      var stub = HealthGrpc.newBlockingStub(channel).withDeadlineAfter(1, TimeUnit.SECONDS);
      var request =
          HealthCheckRequest.newBuilder().setService(service == null ? "" : service).build();
      var response = stub.check(request);
      return response.getStatus() == HealthCheckResponse.ServingStatus.SERVING ? 1 : 0;
    } catch (StatusRuntimeException ex) {
      var code = ex.getStatus().getCode();
      if (code == Status.Code.UNIMPLEMENTED) {
        return 404;
      }
      return -1;
    } catch (Exception ex) {
      logger.error("Error during GRPC health check", ex);
      return -1;
    }
  }

  /**
   * Shuts down a channel opened by {@link #openChannel}.
   *
   * @param channel the channel to release
   */
  static void closeChannel(ManagedChannel channel) {
    channel.shutdownNow();
    try {
      channel.awaitTermination(1, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static ChannelCredentials buildTlsCredentials(String trustPath) {
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
}
