package me.seroperson.reload.live.webserver.classloader;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Objects;

/** A ClassLoader that only uses resources from its parent */
public class DelegatedResourcesClassLoader extends NamedURLClassLoader {

	public DelegatedResourcesClassLoader(String name, URL[] urls, ClassLoader parent) {
		super(name, urls, parent);
		Objects.requireNonNull(parent);
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		return getParent().getResources(name);
	}
}
