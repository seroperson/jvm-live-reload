package me.seroperson.reload.live.runner;

import java.io.Closeable;
import me.seroperson.reload.live.build.BuildLink;

/** Dev server */
public interface DevServer extends Closeable {
	BuildLink buildLink();

	/** Reloads the application. */
	void reload();
}
