package me.seroperson.reload.live.webserver;

import me.seroperson.reload.live.build.ReloadableServer;
import me.seroperson.reload.live.build.BuildLink;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HandlerWrapper;
import io.undertow.util.AttachmentKey;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;

public class ReloadHandler implements HttpHandler {

	public static final AttachmentKey<Boolean> WAS_RELOADED = AttachmentKey.create(Boolean.class);

	private final ReloadableServer server;
	private final HttpHandler next;

	public ReloadHandler(ReloadableServer server, HttpHandler next) {
		this.server = server;
		this.next = next;
	}

	@Override
	public void handleRequest(HttpServerExchange httpServerExchange) throws Exception {
		try {
			// while (isRestarting.get()) {
			// System.out.println("Waiting while restarting ...");
			// Thread.sleep(1000L);
			// }

			var wasReloaded = server.reload();
			while (!isHealthy("localhost", 8080)) {
				Thread.sleep(250L);
			}
			httpServerExchange.setRelativePath(httpServerExchange.getRequestPath());
			httpServerExchange.putAttachment(WAS_RELOADED, wasReloaded);

			System.out.println("Was reloaded: " + wasReloaded);

			next.handleRequest(httpServerExchange);
		} catch (Throwable e) {
			System.out.println("Catch exception " + e);
			e.printStackTrace();
		}
	}

	private boolean isHealthy(String host, int port) throws URISyntaxException, IOException, InterruptedException {
		try {
			HttpClient client = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder().uri(new URI("http://" + host + ":" + port + "/health")).GET()
					.build();
			return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
		} catch (Exception e) {
			return false;
		}
	}

	public static class Wrapper implements HandlerWrapper {

		private ReloadableServer server;

		public Wrapper(ReloadableServer server) {
			this.server = server;
		}

		@Override
		public HttpHandler wrap(HttpHandler handler) {
			return new ReloadHandler(server, handler);
		}
	}
}
