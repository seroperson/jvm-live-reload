package me.seroperson.reload.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import me.seroperson.reload.live.build.BuildLink;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;
import org.junit.jupiter.api.Test;

/**
 * Covers the reload-failure path of {@link BaseDevServerStart}: when {@code startInternal()} throws
 * after {@code stopInternal()} has already cleared the previous generation, the rollback path must
 * run {@code stopInternal()} again so the wrapper is left with no half-installed generation, and
 * {@code reload()} must surface the failure as {@link UnrecoverableException} so callers can shut
 * down the dev server cleanly instead of silently proxying to a dead app.
 */
class BaseDevServerStartTest {

  @Test
  void reloadSurfacesStartupFailureAsUnrecoverable() {
    var buildLink = new StubBuildLink(new ReloadGeneration(1, getClass().getClassLoader()));
    var server = new TestDevServerStart(buildLink);
    server.start();

    server.prepareShouldThrow = true;
    var thrown = assertThrows(UnrecoverableException.class, server::reload);
    assertInstanceOf(RuntimeException.class, thrown.getCause());
    assertEquals("prepare boom", thrown.getCause().getMessage());

    // After a failed reload the wrapper must be in a clean state: no half-installed generation
    // and no leftover app thread. Without the fix in this PR, classLoader / appThread would
    // stay populated, hiding the failure from the next reload() call.
    assertNull(server.classLoader);
    assertNull(server.appThread);
  }

  @Test
  void reloadCleanupRunsBeforeRethrow() {
    var buildLink = new StubBuildLink(new ReloadGeneration(1, getClass().getClassLoader()));
    var server = new TestDevServerStart(buildLink);
    server.start();

    server.prepareShouldThrow = true;
    assertThrows(UnrecoverableException.class, server::reload);

    // cleanupServerForOldGeneration must run on the rollback path; the running counter proves
    // stopInternal() was invoked from startInternal()'s catch block, not just from the outer
    // stopInternal() that runs before startInternal() in reload().
    assertEquals(2, server.cleanupCalls.get());
  }

  @Test
  void reloadReturnsFalseWhenNothingChanged() {
    var server = new TestDevServerStart(new StubBuildLink(null));
    server.start();

    assertEquals(false, server.reload());
    assertNull(server.classLoader);
    assertNull(server.appThread);
  }

  @Test
  void reloadPropagatesCompileFailure() {
    var compileError = new IllegalStateException("compile broken");
    var server = new TestDevServerStart(new StubBuildLink(compileError));
    server.start();

    var thrown = assertThrows(RuntimeException.class, server::reload);
    assertTrue(thrown.getCause() == compileError, "compile failure must be surfaced");
  }

  private static final class StubBuildLink implements BuildLink {
    private final Object result;

    StubBuildLink(Object result) {
      this.result = result;
    }

    @Override
    public Object reload() {
      return result;
    }

    @Override
    public void close() {}
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

  /**
   * Bare {@link BaseDevServerStart} subclass that drives the rollback path entirely from
   * test-controlled flags: {@code prepareShouldThrow} makes {@code prepareServerForNewGeneration}
   * throw, simulating "proxy worker re-creation failed" / "startup hook threw". The {@code
   * mainClass} we hand the appThread does not exist; that is fine because none of these tests let
   * the appThread reach the reflective {@code Class.forName} call (the failing branches throw from
   * prepare).
   */
  private static final class TestDevServerStart extends BaseDevServerStart<Object> {
    volatile boolean prepareShouldThrow = false;
    final AtomicInteger cleanupCalls = new AtomicInteger(0);

    TestDevServerStart(BuildLink buildLink) {
      super(
          new DevServerSettings(List.of(), List.of(), Map.of()),
          buildLink,
          new NoopLogger(),
          "does.not.matter.Main",
          List.of(),
          List.of());
    }

    @Override
    public void start() {
      appThreadGroup = new ThreadGroup("test-app");
      isRunning.set(true);
    }

    @Override
    protected void prepareServerForNewGeneration() {
      if (prepareShouldThrow) {
        throw new RuntimeException("prepare boom");
      }
    }

    @Override
    protected void cleanupServerForOldGeneration() {
      cleanupCalls.incrementAndGet();
    }

    @Override
    protected void stopProxyServer() {}

    @Override
    public String getProxyUrl() {
      return "";
    }

    @Override
    public String getApplicationUrl() {
      return "";
    }
  }
}
