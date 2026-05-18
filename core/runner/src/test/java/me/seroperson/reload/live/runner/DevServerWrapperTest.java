package me.seroperson.reload.live.runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.build.ReloadableServer;
import me.seroperson.reload.live.reflect.ShutdownHook;
import me.seroperson.reload.live.settings.DevServerSettings;
import org.junit.jupiter.api.Test;

/**
 * Covers the failure paths of {@link DevServerWrapper}: a never-started wrapper, and a wrapper
 * whose underlying server threw from {@code start()}, must both be safely closeable and restore the
 * host build process's shutdown-hooks snapshot.
 */
class DevServerWrapperTest {

  @Test
  void closeOnNeverStartedWrapperIsSafeNoop() {
    var wrapper = newWrapper(new NoopServer());
    assertDoesNotThrow(wrapper::close);
  }

  @Test
  void closeIsIdempotent() {
    var wrapper = newWrapper(new NoopServer());
    assertDoesNotThrow(wrapper::start);
    assertDoesNotThrow(wrapper::close);
    assertDoesNotThrow(wrapper::close);
  }

  @Test
  void closeRestoresShutdownHooksWhenStartFails() {
    var hooksBefore = new IdentityHashMap<>(ShutdownHook.getRegistredShutdownHooks());

    var wrapper = newWrapper(new FailingServer());
    assertThrows(RuntimeException.class, wrapper::start);
    // runBackground's catch invokes close() to restore state; emulate that here.
    assertDoesNotThrow(wrapper::close);

    var hooksAfter = ShutdownHook.getRegistredShutdownHooks();
    assertSame(hooksBefore.size(), hooksAfter.size());
    hooksBefore.keySet().forEach(t -> assertSame(t, hooksAfter.get(t)));
  }

  private static DevServerWrapper newWrapper(ReloadableServer server) {
    var settings = new DevServerSettings(List.of(), List.of(), Map.of());
    var params =
        new StartParams(
            settings, List.of(), List.of(), "", "", List.of(), List.of(), Map.<String, String>of());
    return new DevServerWrapper(params, new NoopLogger(), server);
  }

  private static final class NoopLogger implements BuildLogger {
    @Override
    public void info(String message) {}

    @Override
    public void debug(String message) {}

    @Override
    public void warn(String message) {}

    @Override
    public void error(String message) {}

    @Override
    public void error(Throwable t) {}

    @Override
    public void error(String message, Throwable throwable) {}
  }

  private static class NoopServer implements ReloadableServer {
    @Override
    public void start() {}

    @Override
    public boolean isRunning() {
      return false;
    }

    @Override
    public boolean reload() {
      return false;
    }

    @Override
    public String getProxyUrl() {
      return "";
    }

    @Override
    public String getApplicationUrl() {
      return "";
    }

    @Override
    public void close() throws IOException {}
  }

  private static final class FailingServer extends NoopServer {
    @Override
    public void start() {
      throw new RuntimeException("boom");
    }
  }
}
