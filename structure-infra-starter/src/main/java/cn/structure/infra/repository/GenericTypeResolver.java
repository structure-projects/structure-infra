/*
Copyright 2023 Structure Projects

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	`http://www.apache.org/licenses/LICENSE-2.0`

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

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