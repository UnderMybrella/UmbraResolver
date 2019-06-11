package org.abimon.umbra;

import sun.reflect.Reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;

public class PenumbraClassLoader extends ClassLoader {
    public static Field parentField;
    public PenumbraClassLoader(ClassLoader originalClassLoader) throws IllegalAccessException {
        super(originalClassLoader.getParent());

        parentField.set(originalClassLoader, this);
    }

    public PenumbraClassLoader() throws IllegalAccessException {
        this(Reflection.getCallerClass().getClassLoader());
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        int count = 0;
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().equals(PenumbraClassLoader.class.getName())) {
                count++;
            }
        }

        if (count > 2) {
            throw new ClassNotFoundException();
        }
        return Umbra.classForName(name);
    }

    @Override
    protected URL findResource(String name) {
        int count = 0;
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().equals(PenumbraClassLoader.class.getName())) {
                count++;
            }
        }

        if (count > 2) {
            return null;
        }

        return Umbra.resourceForName(name);
    }

    static {
        try {
            parentField = ClassLoader.class.getDeclaredField("parent");
            parentField.setAccessible(true);

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(parentField, parentField.getModifiers() & ~Modifier.FINAL);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
