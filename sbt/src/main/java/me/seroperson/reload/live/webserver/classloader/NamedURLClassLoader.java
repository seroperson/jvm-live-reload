package me.seroperson.reload.live.webserver.classloader;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

/** A ClassLoader with a toString() that prints name/urls. */
public class NamedURLClassLoader extends URLClassLoader {

	private final String name;

	public NamedURLClassLoader(String name, URL[] urls, ClassLoader parent) {
		super(urls, parent);
		this.name = name;
	}

	@Override
	public String toString() {
		return name + Arrays.toString(getURLs());
	}
}
