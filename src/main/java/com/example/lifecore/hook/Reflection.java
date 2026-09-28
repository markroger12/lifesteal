package com.example.lifecore.hook;

import java.lang.reflect.Method;

/**
 * Reflection helpers for optional integrations that are not available at compile time.
 */
public final class Reflection {

    private Reflection() {
    }

    /**
     * Finds a public method, preferring declarations on public interfaces/superclasses so it can be
     * invoked even if the runtime class itself is not public.
     */
    public static Method findMethod(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        Method method = searchPublic(type, name, parameters);
        if (method != null) {
            return method;
        }
        Method fallback = type.getMethod(name, parameters);
        fallback.setAccessible(true);
        return fallback;
    }

    private static Method searchPublic(Class<?> type, String name, Class<?>... parameters) {
        if (type == null) {
            return null;
        }
        if (java.lang.reflect.Modifier.isPublic(type.getModifiers())) {
            try {
                return type.getMethod(name, parameters);
            } catch (NoSuchMethodException ignored) {
                // continue searching
            }
        }
        for (Class<?> iface : type.getInterfaces()) {
            Method found = searchPublic(iface, name, parameters);
            if (found != null) {
                return found;
            }
        }
        return searchPublic(type.getSuperclass(), name, parameters);
    }
}
