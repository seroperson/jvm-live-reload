package me.seroperson.reload.live.hook;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.stub.ServerCalls;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;
import org.junit.jupiter.api.Test;

class GrpcHealthCheckStartupHookTest {

  @Test
  void waitsUntilServingInsteadOfReturningOnPortBind() throws Exception {
    int port = freePort();
    AtomicBoolean ready = new AtomicBoolean(false);
    HealthStatusManager health = new HealthStatusManager();
    health.setStatus("", HealthCheckResponse.ServingStatus.NOT_SERVING);

    Server server =
        ServerBuilder.forPort(port)
            .addService(new GreeterService(ready))
            .addService(health.getHealthService())
            .build()
            .start();

    Thread promoter =
        new Thread(
            () -> {
              try {
                Thread.sleep(1000L);
                ready.set(true);
                health.setStatus("", HealthCheckResponse.ServingStatus.SERVING);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });
    promoter.start();

    DevServerSettings settings =
        new DevServerSettings(
            List.of(),
            List.of(),
            Map.of(
                DevServerSettings.LiveReloadGrpcHost,
                "localhost",
                DevServerSettings.LiveReloadGrpcPort,
                String.valueOf(port),
                DevServerSettings.LiveReloadGrpcHealthService,
                ""));

    try {
      long start = System.nanoTime();
      new GrpcHealthCheckStartupHook()
          .hook(new Thread(), ClassLoader.getSystemClassLoader(), settings, new NoopLogger());
      long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

      assertTrue(
          elapsedMs >= 900L,
          "startup hook returned too early after only "
              + elapsedMs
              + "ms while health was NOT_SERVING");
      assertTrue(ready.get(), "server should be marked ready before startup hook returns");
    } finally {
      server.shutdownNow();
      server.awaitTermination();
    }
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static final class GreeterService implements BindableService {
    private final AtomicBoolean ready;

    private GreeterService(AtomicBoolean ready) {
      this.ready = ready;
    }

    @Override
    public ServerServiceDefinition bindService() {
      MethodDescriptor<byte[], byte[]> methodDescriptor =
          MethodDescriptor.<byte[], byte[]>newBuilder()
              .setType(MethodDescriptor.MethodType.UNARY)
              .setFullMethodName("greeter.Greeter/Greet")
              .setRequestMarshaller(new ByteArrayMarshaller())
              .setResponseMarshaller(new ByteArrayMarshaller())
              .build();

      ServerCallHandler<byte[], byte[]> handler =
          ServerCalls.asyncUnaryCall(
              (request, responseObserver) -> {
                if (!ready.get()) {
                  responseObserver.onError(
                      Status.UNAVAILABLE.withDescription("warming up").asRuntimeException());
                } else {
                  responseObserver.onNext("READY".getBytes());
                  responseObserver.onCompleted();
                }
              });

      return ServerServiceDefinition.builder("greeter.Greeter")
          .addMethod(methodDescriptor, handler)
          .build();
    }
  }

  private static final class ByteArrayMarshaller implements MethodDescriptor.Marshaller<byte[]> {
    @Override
    public InputStream stream(byte[] value) {
      return new ByteArrayInputStream(value);
    }

    @Override
    public byte[] parse(InputStream stream) {
      try {
        return stream.readAllBytes();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static final class NoopLogger implements BuildLogger {
    @Override
    public void debug(String message) {}

    @Override
    public void info(String message) {}

    @Override
    public void warn(String message) {}

    @Override
    public void error(Throwable t) {}

    @Override
    public void error(String message, Throwable t) {}

    @Override
    public void error(String message) {}
  }
}
