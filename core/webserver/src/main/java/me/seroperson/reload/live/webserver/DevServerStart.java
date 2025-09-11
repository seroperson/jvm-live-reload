package me.seroperson.reload.live.webserver;

import me.seroperson.reload.live.build.BuildLink;
import me.seroperson.reload.live.build.ReloadableServer;

import io.javalin.Javalin;

public class DevServerStart implements ReloadableServer {

	public static ReloadableServer start(BuildLink build/* , int port, String address */) {
		System.out.println("Starting DevServerStart");
		var server = new DevServerStart();
		return server;
	}

	private Javalin j;

	public DevServerStart() {
		j = Javalin.create().get("/", ctx -> ctx.result("Hello World")).start(9000);
		System.out.println("Started!");
	}

	@Override
	public void stop() {
		j.stop();
		System.out.println("Stopped");
	}

	@Override
	public void reload() {
		System.out.println("Reloaded");
	}

}
