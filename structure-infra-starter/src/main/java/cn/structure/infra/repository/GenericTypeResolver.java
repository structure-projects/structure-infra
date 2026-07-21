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
        return resolveType(clazz, RepositoryDelegate.class, 0);
    }

    public static Class<?> resolvePoClass(Class<?> clazz) {
        return resolveType(clazz, RepositoryDelegate.class, 1);
    }

    public static Class<?> resolveIdClass(Class<?> clazz) {
        return resolveType(clazz, RepositoryDelegate.class, 2);
    }

    private GenericTypeResolver() {
    }
}
