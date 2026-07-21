package cn.structure.infra.repository;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class GenericTypeResolver {

    public static Class<?> resolveType(Class<?> clazz, Class<?> targetInterface, int index) {
        Type genericSuperclass = clazz.getGenericSuperclass();
        while (genericSuperclass != null) {
            if (genericSuperclass instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericSuperclass;
                Type rawType = parameterizedType.getRawType();
                if (targetInterface.isAssignableFrom((Class<?>) rawType)) {
                    Type typeArg = parameterizedType.getActualTypeArguments()[index];
                    if (typeArg instanceof Class) {
                        return (Class<?>) typeArg;
                    } else if (typeArg instanceof ParameterizedType) {
                        return (Class<?>) ((ParameterizedType) typeArg).getRawType();
                    }
                }
            }
            clazz = clazz.getSuperclass();
            if (clazz == null) {
                break;
            }
            genericSuperclass = clazz.getGenericSuperclass();
        }

        Type[] interfaces = clazz.getGenericInterfaces();
        for (Type iface : interfaces) {
            if (iface instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) iface;
                Type rawType = parameterizedType.getRawType();
                if (targetInterface.isAssignableFrom((Class<?>) rawType)) {
                    Type typeArg = parameterizedType.getActualTypeArguments()[index];
                    if (typeArg instanceof Class) {
                        return (Class<?>) typeArg;
                    } else if (typeArg instanceof ParameterizedType) {
                        return (Class<?>) ((ParameterizedType) typeArg).getRawType();
                    }
                }
            }
        }

        return null;
    }

    public static Class<?> resolveEntityClass(Class<?> clazz) {
        Class<?> result = resolveFromSuperclass(clazz, 0);
        if (result != null) {
            return result;
        }
        return resolveType(clazz, RepositoryDelegate.class, 0);
    }

    public static Class<?> resolvePoClass(Class<?> clazz) {
        Class<?> result = resolveFromSuperclass(clazz, 1);
        if (result != null) {
            return result;
        }
        return resolveType(clazz, RepositoryDelegate.class, 1);
    }

    public static Class<?> resolveIdClass(Class<?> clazz) {
        Class<?> result = resolveFromSuperclass(clazz, 2);
        if (result != null) {
            return result;
        }
        return resolveType(clazz, RepositoryDelegate.class, 1);
    }

    private static Class<?> resolveFromSuperclass(Class<?> clazz, int index) {
        Type genericSuperclass = clazz.getGenericSuperclass();
        while (genericSuperclass != null) {
            if (genericSuperclass instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericSuperclass;
                Type[] typeArgs = parameterizedType.getActualTypeArguments();
                if (typeArgs.length > index) {
                    Type typeArg = typeArgs[index];
                    if (typeArg instanceof Class) {
                        return (Class<?>) typeArg;
                    } else if (typeArg instanceof ParameterizedType) {
                        return (Class<?>) ((ParameterizedType) typeArg).getRawType();
                    }
                }
            }
            clazz = clazz.getSuperclass();
            if (clazz == null) {
                break;
            }
            genericSuperclass = clazz.getGenericSuperclass();
        }
        return null;
    }

    private GenericTypeResolver() {
    }
}