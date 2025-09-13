package me.seroperson.reload.live.webserver;

import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.build.BuildLink;
import me.seroperson.reload.live.build.ReloadableServer;
import me.seroperson.reload.live.hook.Hook;

import java.time.Duration;
import java.io.Closeable;
import java.util.List;
import java.net.URI;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.server.handlers.proxy.SimpleProxyClientProvider;

public class DevServerStart implements ReloadableServer {

	private Undertow server;
	private Thread appThread;
	private ClassLoader classLoader;
	private String mainClass;
	private List<Hook> shutdownHooks;
	private final Duration shutdownPollingInterval;
	private final BuildLogger logger;

	public static <T> T ignoringExc(RunnableExc<T> r) {
		try {
			return r.run();
		} catch (Exception e) {
		}
		return null;
	}

	@FunctionalInterface
	public interface RunnableExc<T> {
		T run() throws Exception;
	}

	// https://stackoverflow.com/a/48828373/5246998
	public static boolean isTcpPortAvailable(int port) {
		try (ServerSocket serverSocket = new ServerSocket()) {
			// setReuseAddress(false) is required only on macOS,
			// otherwise the code will not work correctly on that platform
			serverSocket.setReuseAddress(false);
			serverSocket.bind(new InetSocketAddress(InetAddress.getByName("localhost"), port), 1);
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	public DevServerStart(BuildLink buildLink, BuildLogger logger, String mainClass, List<String> shutdownHookClasses) {

		this.mainClass = mainClass;
		this.shutdownPollingInterval = Duration.ofSeconds(5);
		this.logger = logger;

		shutdownHooks = shutdownHookClasses.stream()
				.map((v) -> ignoringExc(() -> (Hook) Class.forName(v).newInstance())).toList();

		var proxyClientProvider = new SimpleProxyClientProvider(URI.create("http://localhost:8080"));
		var handler = new ProxyHandler(proxyClientProvider, 30000, ResponseCodeHandler.HANDLE_404);
		server = Undertow.builder().addHttpListener(9000, "localhost").setHandler(new HttpHandler() {
			@Override
			public void handleRequest(HttpServerExchange httpServerExchange) throws Exception {
				var reloadResult = buildLink.reload();
				if (reloadResult instanceof ClassLoader) {// New application classes
					stopInternal();
					startInternal((ClassLoader) reloadResult);
					// reload();
				} else if (reloadResult == null) {// No change in the application classes
					;
				} else if (reloadResult instanceof Throwable) {
					// case NonFatal(t) => Failure(t) // An error we can display
					// case t: Throwable => throw t // An error that we can't handle
					throw new RuntimeException((Throwable) reloadResult);
				}

				httpServerExchange.setRelativePath(httpServerExchange.getRequestPath());
				if (!isTcpPortAvailable(8080)) {
					handler.handleRequest(httpServerExchange);
				} else {
					logger.info("Waiting for the server to become available ...");
					while (isTcpPortAvailable(8080)) {
						Thread.sleep(1000L);
					}
					handler.handleRequest(httpServerExchange);
				}
			}
		}).build();

		server.start();
	}

	private void startInternal(ClassLoader classLoader) {
		this.classLoader = classLoader;
		this.appThread = new Thread(() -> {
			Thread.currentThread().setName("main");
			try {
				Class<?> clazz = classLoader.loadClass(mainClass);
				var mainMethod = clazz.getMethod("main", String[].class);
				mainMethod.invoke(null, (Object) new String[0]);
			} catch (Exception e) {
				if (e.getCause() instanceof InterruptedException) {
					logger.info("Application thread was interrupted");
				} else {
					logger.error("Failed to invoke main method on " + mainClass + ".");
					stopInternal();
					throw new RuntimeException(e);
				}
			}
		});
		appThread.start();
	}

	private synchronized void stopInternal() {
		if (appThread != null) {
			logger.info("Stopping " + mainClass);
			appThread.interrupt();

			shutdownHooks.forEach((v) -> v.hook());

			try {
				appThread.join(shutdownPollingInterval.toMillis());
				while (appThread.isAlive()) {
					logger.warn("Application thread is still running after interrupt.");
					appThread.join(shutdownPollingInterval.toMillis());
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			appThread = null;
		}

		if (classLoader != null) {
			logger.info("Cleaning up old instances");
			if (classLoader instanceof Closeable) {
				try {
					((Closeable) classLoader).close();
				} catch (Exception e) {
					logger.error("Failed to close class loader: " + e.getMessage());
				}
			}
			classLoader = null;
			System.gc();
		}
	}

	@Override
	public void stop() {
		server.stop();
		stopInternal();
	}

	@Override
	public void reload() {
		// stopInternal();
		// startInternal();
	}

}
