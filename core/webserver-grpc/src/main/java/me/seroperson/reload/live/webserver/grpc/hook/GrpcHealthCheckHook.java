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
 * Base class for hooks that probe the GRPC health checking protocol of the target server.
 *
 * <p>A single {@link ManagedChannel} is created on the first {@link #isHealthy} call within a
 * {@link #hook} invocation and reused for all subsequent polls. Subclasses must call {@link
 * #closeChannel()} — typically in a {@code finally} block — when their {@code hook()} method
 * returns so the channel is shut down promptly.
 *
 * <p>The {@code service} field of the health-check request can be configured via {@link
 * DevServerSettings#getGrpcHealthService()}; an empty string queries the overall server health as
 * specified by the protocol.
 */
abstract class GrpcHealthCheckHook implements Hook {

  private ManagedChannel channel;

  /**
   * Probes the target GRPC server for health, reusing the same channel across calls.
   *
   * @param logger build logger
   * @param settings dev server settings (host, port, service name, TLS flag)
   * @return 1 SERVING, 0 NOT_SERVING/UNKNOWN, -1 connection error, 404 health service not
   *     implemented by the target
   */
  protected final int isHealthy(BuildLogger logger, DevServerSettings settings) {
    if (channel == null) {
      channel = buildChannel(settings);
    }
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

  /** Shuts down the shared channel. Must be called once the polling loop exits. */
  protected final void closeChannel() {
    ManagedChannel ch = channel;
    channel = null;
    if (ch != null) {
      ch.shutdownNow();
      try {
        ch.awaitTermination(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private ManagedChannel buildChannel(DevServerSettings settings) {
    ChannelCredentials credentials =
        settings.isGrpcTargetTls()
            ? buildTlsCredentials(settings.getGrpcTargetTlsTrust())
            : InsecureChannelCredentials.create();
    return Grpc.newChannelBuilderForAddress(
            settings.getGrpcHost(), settings.getGrpcPort(), credentials)
        .build();
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
