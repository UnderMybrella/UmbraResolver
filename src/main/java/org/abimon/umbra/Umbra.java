package org.abimon.umbra;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Pattern;

public class Umbra {
    /**
     * Should we fail 'hard and fast', or soft and silently?
     * Default to soft
     */
    public static boolean failHard = false;

    private Umbra() {
    }

    /**
     * A set of PenumbraClassLoaders that have been created through Umbra
     */
    public static Set<PenumbraClassLoader> penumbraClassLoaders = new HashSet<>();

    /**
     * A set of ClassLoaders to use as sources
     */
    public static Set<ClassLoader> classloaders = new HashSet<>();

    /**
     * Register a class loader for use within Umbra
     * @param classloader A class loader
     */
    public static void registerClassloader(ClassLoader classloader) {
        classloaders.add(classloader);
    }

    /**
     * Register a directory to load jar files from
     * @param dir A directory
     * @throws IOException
     */
    public static void registerClassloader(File dir) throws IOException {
        classloaders.add(new UmbraClassLoader(dir));
    }

    /**
     * Register an array of URLs as a class loader
     * @param urls An array of URLs to be registered
     */
    public static void registerClassloader(URL... urls) {
        classloaders.add(new URLClassLoader((urls)));
    }

    /**
     * Check the dependencies of this program, and download any that are missing to libDir
     *
     * @param libDir The folder to download libraries to
     * @return true if successful, false if otherwise
     */
    public static boolean checkDependencies(File libDir) {
        if (libDir == null)
            throw new IllegalArgumentException("libDir must not be null!");

        if (!libDir.isDirectory() || !libDir.exists())
            throw new IllegalArgumentException("libDir must be a directory that exists!");

        InputStream umbra = Umbra.class.getClassLoader().getResourceAsStream("META-INF/.umbra");

        if (umbra == null) {
            if (failHard) {
                throw new IllegalStateException("No umbra meta file can be found");
            } else {
                return false;
            }
        }

        List<String> repositories = new ArrayList<>();
        List<GradleDependency> dependencies = new ArrayList<>();

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(umbra));
            reader.lines().forEach(line -> {
                if (line.startsWith("repository:")) {
                    repositories.add(line.replace("repository:", "").trim());
                } else if (line.startsWith("dependency:")) {
                    String[] depComponents = line.replace("dependency:", "").trim().split(":");
                    if (depComponents.length == 4) {
                        dependencies.add(new GradleDependency(depComponents[0], depComponents[1], depComponents[2], depComponents[3]));
                    }
                }
            });
        } finally {
            try {
                umbra.close();
            } catch (IOException ignored) {
            }
        }

        dependencies.forEach(dep -> {
            Pattern otherVersions = Pattern.compile("\\Q" + dep.group + "_" + dep.module + "_" + "\\E.*\\.jar");
            Pattern ourVersion = Pattern.compile("\\Q" + dep.group + "_" + dep.module + "_" + dep.version + ".jar\\E");
            try {
                Files.walk(libDir.toPath())
                        .filter(path -> otherVersions.matcher(path.getFileName().toString()).matches())
                        .filter(path -> !ourVersion.matcher(path.getFileName().toString()).matches())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        if (classloaders.stream().noneMatch(loader ->
                (loader instanceof UmbraClassLoader) && ((UmbraClassLoader) loader).directory.equals(libDir))) {
            try {
                registerClassloader(libDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        dependencies.forEach(dep -> {
            if (resourceForName(dep.classFile) == null) {
                File destination = new File(libDir, dep.qualifier + ".jar");

                repositories.forEach(repository -> {
                    StringBuilder builder = new StringBuilder();
                    builder.append(repository);
                    if (repository.charAt(repository.length() - 1) != '/')
                        builder.append('/');

                    builder.append(dep.group.replace('.', '/'));
                    builder.append('/');
                    builder.append(dep.module);
                    builder.append('/');
                    builder.append(dep.version);
                    builder.append('/');
                    builder.append(dep.module);
                    builder.append('-');
                    builder.append(dep.version);
                    builder.append(".jar");

                    try {
                        URL url = new URL(builder.toString());

                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("User-Agent", "Umbra (https://github.com/UnderMybrella/Umbra)");
                        conn.connect();

                        InputStream input = conn.getInputStream();

                        if (conn.getResponseCode() == 200) {
                            Files.copy(input, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            rescan();
                        }
                    } catch (IOException ignored) {
                    }
                });
            }
        });

        return true;
    }

    public static Class classForName(String name) {
        for (ClassLoader classloader : classloaders) {
            try {
                return classloader.loadClass(name.replace(".class", ""));
            } catch (ClassNotFoundException ignored) {
            }
        }

        return null;
    }

    public static URL resourceForName(String name) {
        for (ClassLoader classloader : classloaders) {
            URL url = classloader.getResource(name);
            if (url != null)
                return url;
        }

        return null;
    }

    public static boolean isClassPresent(String name) {
        String loading = name.replace(".class", "").replace('.', '/').concat(".class");
        return resourceForName(loading) != null;
    }

    public static ClassLoader classLoaderForClass(String name) {
        return classLoaderForClass(name, false);
    }
    public static ClassLoader classLoaderForClass(String name, boolean ignorePenumbra) {
        String loading = name.replace(".class", "").replace('.', '/').concat(".class");

        for (ClassLoader classloader : classloaders) {
            if (ignorePenumbra && (classloader instanceof PenumbraClassLoader || classloader.getParent() instanceof PenumbraClassLoader))
                continue;

            if (classloader.getResource(loading) != null)
                return classloader;
        }

        return null;
    }

    public static void rescan() {
        for (ClassLoader classLoader : classloaders) {
            if (classLoader instanceof UmbraClassLoader) {
                try {
                    ((UmbraClassLoader) classLoader).rescan();
                } catch (IOException ignored) { }
            }
        }
    }

    /**
     * Hook the system class loader with a PenumbraClassLoader
     * @throws IllegalAccessException
     */
    public static void hookSystem() throws IllegalAccessException {
        penumbraClassLoaders.add(new PenumbraClassLoader(ClassLoader.getSystemClassLoader()));
    }

    /**
     * Hook the calling class with a PenumbraClassLoader
     * @throws IllegalAccessException
     */
    public static void hookClassLoader() throws IllegalAccessException {
        Class clazz = classForName(Thread.currentThread().getStackTrace()[2].getClassName());
        penumbraClassLoaders.add(new PenumbraClassLoader(Objects.requireNonNull(clazz).getClassLoader()));
    }

    static {
        classloaders.add(ClassLoader.getSystemClassLoader());
        classloaders.add(Umbra.class.getClassLoader());

        try {
            hookSystem();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}