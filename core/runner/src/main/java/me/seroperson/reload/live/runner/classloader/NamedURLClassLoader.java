package me.seroperson.reload.live.runner.classloader;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.Arrays;

/**
 * A URLClassLoader with a descriptive toString() method for debugging.
 *
 * <p>
 * This ClassLoader extends URLClassLoader and adds a human-readable name that is included in the
 * toString() output along with the URLs. This makes debugging ClassLoader hierarchies much easier,
 * especially in complex live reload scenarios where multiple ClassLoaders are involved.
 */
public class NamedURLClassLoader extends URLClassLoader {

    private final String name;

    /**
     * Creates a new NamedURLClassLoader.
     *
     * @param name   the descriptive name for this ClassLoader
     * @param urls   the URLs from which to load classes and resources
     * @param parent the parent ClassLoader
     */
    public NamedURLClassLoader(String name, URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.name = name;
    }

    @Override
    public URL findResource(String name) {
        // if (System.out != null) {
        // System.out.println(this.name + ": trying to get resource via findResource: " + name);
        // }
        return super.findResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        // if (System.out != null) {
        // System.out.println(this.name + ": trying to get resource via getResources: " + name);
        // }
        return super.getResources(name);
    }

    /**
     * Returns a string representation that includes the name and URLs.
     *
     * @return a string in the format "name[url1, url2, ...]"
     */
    @Override
    public String toString() {
        return name + Arrays.toString(getURLs());
    }
}
