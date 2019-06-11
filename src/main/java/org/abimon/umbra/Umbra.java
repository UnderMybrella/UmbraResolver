package org.abimon.umbra;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class Umbra {
    /**
     * Should we fail 'hard and fast', or soft and silently?
     * Default to soft
     */
    public static boolean failHard = false;

    private Umbra() {
    }

    public static Set<PenumbraClassLoader> penumbraClassLoaders = new HashSet<>();
    public static Set<ClassLoader> classloaders = new HashSet<>();

    public static void registerClassloader(ClassLoader classloader) {
        classloaders.add(classloader);
    }

    public static void registerClassloader(File dir) throws IOException {
        classloaders.add(new UmbraClassLoader(dir));
    }

    public static void registerClassloader(URL... urls) {
        classloaders.add(new URLClassLoader((urls)));
    }

    public static boolean checkDependencies(File libDir) {
        if (libDir == null)
            throw new IllegalArgumentException("libDir must not be null!");

        if (libDir.isDirectory() && !libDir.exists())
            libDir.mkdirs();

        if (!libDir.isDirectory() || !libDir.exists())
            throw new IllegalArgumentException("libDir must be a directory that exists!");

        if (classloaders.stream().noneMatch(loader ->
                (loader instanceof UmbraClassLoader) && ((UmbraClassLoader) loader).directory.equals(libDir))) {
            try {
                registerClassloader(libDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

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
            if (!isClassPresent(dep.classFile)) {
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

                        if (conn.getResponseCode() != 200) {
                        } else {
                            Files.copy(input, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            rescan();
                        }
                    } catch (MalformedURLException malformed) {
                    } catch (IOException e) {
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

    public static void rescan() {
        for (ClassLoader classLoader : classloaders) {
            if (classLoader instanceof UmbraClassLoader) {
                try {
                    ((UmbraClassLoader) classLoader).rescan();
                } catch (IOException ignored) { }
            }
        }
    }

    public static void hookSystem() throws IllegalAccessException {
        penumbraClassLoaders.add(new PenumbraClassLoader(ClassLoader.getSystemClassLoader()));
    }

    public static void hookClassLoader() throws IllegalAccessException {
        Class clazz = classForName(Thread.currentThread().getStackTrace()[2].getClassName());
        penumbraClassLoaders.add(new PenumbraClassLoader(Objects.requireNonNull(clazz).getClassLoader()));
    }

    static {
        classloaders.add(ClassLoader.getSystemClassLoader());
        classloaders.add(Umbra.class.getClassLoader());
    }
}