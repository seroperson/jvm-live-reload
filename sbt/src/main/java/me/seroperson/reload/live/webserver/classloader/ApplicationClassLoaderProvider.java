
package me.seroperson.reload.live.webserver.classloader;

import java.net.URLClassLoader;

public interface ApplicationClassLoaderProvider {
	URLClassLoader get();
}
