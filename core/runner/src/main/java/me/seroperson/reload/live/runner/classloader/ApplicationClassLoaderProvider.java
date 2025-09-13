package me.seroperson.reload.live.runner.classloader;

import java.net.URLClassLoader;

public interface ApplicationClassLoaderProvider {
	URLClassLoader get();
}
