package me.seroperson.reload.live.hook;

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
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Health check hook that uses the GRPC health checking protocol to determine server health.
 *
 * <p>Each probe issues a unary {@code grpc.health.v1.Health/Check} request and treats only {@code
 * SERVING} as healthy. The configured service name is respected; an empty string checks the overall
 * server status.
 */
interface GrpcHealthCheckHook extends HealthCheckHook {

  @Override
  default int isHealthy(BuildLogger logger, String path, String host, int port) {
    return isHealthy(
        logger,
        new DevServerSettings(
            java.util.List.of(),
            java.util.List.of(),
            java.util.Map.of(
                DevServerSettings.LiveReloadGrpcHost,
                host,
                DevServerSettings.LiveReloadGrpcPort,
                String.valueOf(port),
                DevServerSettings.LiveReloadGrpcHealthService,
                path == null ? "" : path)));
  }

  default int isHealthy(BuildLogger logger, DevServerSettings settings) {
    var host = settings.getGrpcHost();
    var port = settings.getGrpcPort();
    var service = settings.getGrpcHealthService();
    ChannelCredentials credentials =
        settings.isGrpcTargetTls()
            ? buildTlsCredentials(settings.getGrpcTargetTlsTrust())
            : InsecureChannelCredentials.create();
    ManagedChannel channel = Grpc.newChannelBuilderForAddress(host, port, credentials).build();
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
    } finally {
      channel.shutdownNow();
      try {
        channel.awaitTermination(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
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
