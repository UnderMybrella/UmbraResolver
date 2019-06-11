package org.abimon.umbra;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class UmbraClassLoader extends URLClassLoader {
    public File directory;

    public UmbraClassLoader(File directory) throws IOException {
        super(new URL[0], ClassLoader.getSystemClassLoader());
        if (directory == null)
            throw new IllegalArgumentException("libDir must not be null!");

        if (!directory.isDirectory() || !directory.exists())
            throw new IllegalArgumentException("libDir must be a directory that exists!");

        this.directory = directory.getAbsoluteFile();
        rescan();
    }

    public void rescan() throws IOException {
//        Arrays.asList(((URLClassLoader) loader).getURLs()).contains(libUrl)
        List<URL> urlList = Arrays.asList(getURLs());
        Files.walk(directory.toPath())
                .filter(file -> file.toString().endsWith(".jar"))
                .map(path -> {
                    try {
                        return path.toUri().toURL();
                    } catch (MalformedURLException e) {
                        throw new IllegalStateException("This should not happen!");
                    }
                })
                .filter(url -> !urlList.contains(url))
                .forEach(this::addURL);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UmbraClassLoader)) return false;
        UmbraClassLoader that = (UmbraClassLoader) o;
        return Objects.equals(directory, that.directory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(directory);
    }
}
