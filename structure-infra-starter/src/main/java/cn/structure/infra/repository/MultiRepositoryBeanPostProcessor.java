package cn.structure.infra.repository;

import cn.structure.infra.properties.InfraProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MultiRepositoryBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware, org.springframework.core.Ordered, org.springframework.beans.factory.SmartInitializingSingleton {

    private DefaultListableBeanFactory beanFactory;

    private final Map<Class<?>, Map<RepositoryType, RepositoryDelegate<?, ?>>> delegateRegistry = new ConcurrentHashMap<>();

    private final Map<Class<?>, RepositoryType> typeCache = new ConcurrentHashMap<>();

    private final List<RepositoryDelegate<?, ?>> unresolvedDelegates = new ArrayList<>();

    private InfraProperties infraProperties;

    public MultiRepositoryBeanPostProcessor() {
        log.info("MultiRepositoryBeanPostProcessor created");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        log.info("MultiRepositoryBeanPostProcessor.setApplicationContext() called");
        this.beanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        this.infraProperties = applicationContext.getBean(InfraProperties.class);
        log.info("MultiRepositoryBeanPostProcessor.infraProperties: {}", infraProperties);
        log.info("MultiRepositoryBeanPostProcessor.multiRepositoryEnabled: {}", infraProperties.getMultiRepositoryEnabled());
    }

    @Override
    public int getOrder() {
        return org.springframework.core.Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void afterSingletonsInstantiated() {
        log.info("MultiRepositoryBeanPostProcessor.afterSingletonsInstantiated() called");
        
        for (RepositoryDelegate<?, ?> delegate : unresolvedDelegates) {
            try {
                processRepositoryDelegate(delegate, delegate.getClass().getSimpleName());
            } catch (Exception e) {
                log.warn("Failed to process delegate after singletons instantiated: {}", e.getMessage());
            }
        }
        unresolvedDelegates.clear();

        log.info("delegateRegistry size: {}", delegateRegistry.size());
        for (Map.Entry<Class<?>, Map<RepositoryType, RepositoryDelegate<?, ?>>> entry : delegateRegistry.entrySet()) {
            log.info("  Entity: {}, delegates: {}", entry.getKey().getSimpleName(), entry.getValue().keySet());
        }
        if (Boolean.TRUE.equals(infraProperties.getMultiRepositoryEnabled())) {
            log.info("Multi-repository enabled, injecting delegates to MultiRepositoryFacade...");
            injectDelegatesToMultiFacades();
        }
    }

    @SuppressWarnings("unchecked")
    private void injectDelegatesToMultiFacades() {
        Map<String, Object> allBeans = beanFactory.getBeansWithAnnotation(Component.class);
        log.info("Found {} Component beans", allBeans.size());

        for (Map.Entry<String, Object> entry : allBeans.entrySet()) {
            Object bean = entry.getValue();
            if (isMultiRepositoryFacade(bean.getClass())) {
                MultiRepositoryFacade facade = (MultiRepositoryFacade) bean;
                log.info("Found MultiRepositoryFacade subclass: {}", entry.getKey());
                try {
                    Type[] typeArgs = resolveTypeArguments(facade.getClass(), MultiRepositoryFacade.class);
                    if (typeArgs != null && typeArgs.length >= 2) {
                        Class<?> entityClass = getRawType(typeArgs[0]);
                        log.info("MultiRepositoryFacade [{}] entityClass: {}", entry.getKey(), entityClass);

                        if (entityClass == null) {
                            log.warn("Entity class is null for MultiRepositoryFacade [{}]", entry.getKey());
                            continue;
                        }

                        Map<RepositoryType, RepositoryDelegate<?, ?>> typeDelegates = delegateRegistry.get(entityClass);
                        if (typeDelegates != null && !typeDelegates.isEmpty()) {
                            for (Map.Entry<RepositoryType, RepositoryDelegate<?, ?>> delegateEntry : typeDelegates.entrySet()) {
                                facade.registerDelegate(delegateEntry.getKey(), (RepositoryDelegate) delegateEntry.getValue());
                            }

                            RepositoryType defaultType = infraProperties.getDefaultRepositoryType();
                            if (defaultType != null && defaultType != RepositoryType.AUTO) {
                                facade.setDefaultType(defaultType);
                            }

                            // 设置默认 delegate 到父类 RepositoryFacade 的 delegate 字段
                            RepositoryDelegate<?, ?> defaultDelegate = typeDelegates.values().iterator().next();
                            if (defaultType != null && defaultType != RepositoryType.AUTO && typeDelegates.containsKey(defaultType)) {
                                defaultDelegate = typeDelegates.get(defaultType);
                            }
                            setParentDelegate(facade, defaultDelegate);

                            log.info("Injected {} delegates to MultiRepositoryFacade [{}], default delegate: {}",
                                    typeDelegates.size(), entry.getKey(), defaultDelegate.getClass().getSimpleName());
                        } else {
                            log.warn("No delegates found for entity class: {}", entityClass);
                        }
                    } else {
                        log.warn("Cannot resolve type arguments for {}", entry.getKey());
                    }
                } catch (Exception e) {
                    log.error("Failed to inject delegates to MultiRepositoryFacade [{}]: {}", entry.getKey(), e.getMessage(), e);
                }
            }
        }
    }

    private boolean isMultiRepositoryFacade(Class<?> clazz) {
        if (clazz == null || clazz == Object.class) {
            return false;
        }
        if (MultiRepositoryFacade.class.isAssignableFrom(clazz)) {
            return true;
        }
        return isMultiRepositoryFacade(clazz.getSuperclass());
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof RepositoryDelegate) {
            log.info("Processing RepositoryDelegate bean: {}, type: {}", beanName, bean.getClass().getName());
            try {
                processRepositoryDelegate((RepositoryDelegate<?, ?>) bean, beanName);
            } catch (Exception e) {
                log.warn("Cannot resolve entity class for delegate {} during initialization, will retry later: {}", beanName, e.getMessage());
                unresolvedDelegates.add((RepositoryDelegate<?, ?>) bean);
            }
        }
        return bean;
    }

    private Type[] resolveTypeArguments(Class<?> clazz, Class<?> targetClass) {
        Type genericSuperclass = clazz.getGenericSuperclass();
        while (genericSuperclass != null) {
            if (genericSuperclass instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericSuperclass;
                Type rawType = parameterizedType.getRawType();
                if (targetClass.equals(rawType)) {
                    return parameterizedType.getActualTypeArguments();
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

    private Class<?> getRawType(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        }
        return null;
    }

    private Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void setParentDelegate(MultiRepositoryFacade facade, RepositoryDelegate<?, ?> delegate) {
        try {
            Field field = findField(facade.getClass(), "delegate");
            if (field != null) {
                field.setAccessible(true);
                field.set(facade, delegate);
                log.debug("Set parent delegate [{}] to MultiRepositoryFacade", delegate.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.warn("Failed to set parent delegate: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void processRepositoryDelegate(RepositoryDelegate<?, ?> delegate, String beanName) {
        Class<?> entityClass = delegate.getEntityClass();
        if (entityClass == null) {
            log.warn("Cannot resolve entity class for delegate: {}", beanName);
            return;
        }

        RepositoryType type = inferRepositoryType(delegate.getClass());
        log.info("Processing repository delegate: entity={}, type={}, bean={}",
                entityClass.getSimpleName(), type, beanName);

        delegateRegistry.computeIfAbsent(entityClass, k -> new ConcurrentHashMap<>())
                .put(type, delegate);
    }

    private RepositoryType inferRepositoryType(Class<?> delegateClass) {
        if (typeCache.containsKey(delegateClass)) {
            return typeCache.get(delegateClass);
        }

        cn.structure.infra.annotations.RepositoryTypeAnnotation annotation =
                AnnotationUtils.findAnnotation(delegateClass, cn.structure.infra.annotations.RepositoryTypeAnnotation.class);
        if (annotation != null) {
            RepositoryType type = (RepositoryType) annotation.value();
            typeCache.put(delegateClass, type);
            return type;
        }

        RepositoryType typeFromSuperclass = findTypeFromSuperclass(delegateClass);
        if (typeFromSuperclass != null) {
            typeCache.put(delegateClass, typeFromSuperclass);
            return typeFromSuperclass;
        }

        RepositoryType typeFromInterface = findTypeFromInterface(delegateClass);
        if (typeFromInterface != null) {
            typeCache.put(delegateClass, typeFromInterface);
            return typeFromInterface;
        }

        String className = delegateClass.getSimpleName().toUpperCase();
        if (className.contains("JPA")) {
            typeCache.put(delegateClass, RepositoryType.JPA);
            return RepositoryType.JPA;
        } else if (className.contains("MYBATIS")) {
            typeCache.put(delegateClass, RepositoryType.MYBATIS_PLUS);
            return RepositoryType.MYBATIS_PLUS;
        } else if (className.contains("MONGO")) {
            typeCache.put(delegateClass, RepositoryType.MONGODB);
            return RepositoryType.MONGODB;
        } else if (className.contains("ELASTICSEARCH")) {
            typeCache.put(delegateClass, RepositoryType.ELASTICSEARCH);
            return RepositoryType.ELASTICSEARCH;
        }

        typeCache.put(delegateClass, RepositoryType.AUTO);
        return RepositoryType.AUTO;
    }

    private RepositoryType findTypeFromSuperclass(Class<?> clazz) {
        Class<?> superclass = clazz.getSuperclass();
        while (superclass != null && superclass != Object.class) {
            cn.structure.infra.annotations.RepositoryTypeAnnotation annotation =
                    AnnotationUtils.findAnnotation(superclass, cn.structure.infra.annotations.RepositoryTypeAnnotation.class);
            if (annotation != null) {
                return annotation.value();
            }

            RepositoryType type = matchBySuperclassType(superclass);
            if (type != null) {
                return type;
            }

            superclass = superclass.getSuperclass();
        }
        return null;
    }

    private RepositoryType findTypeFromInterface(Class<?> clazz) {
        Class<?>[] interfaces = clazz.getInterfaces();
        for (Class<?> iface : interfaces) {
            cn.structure.infra.annotations.RepositoryTypeAnnotation annotation =
                    AnnotationUtils.findAnnotation(iface, cn.structure.infra.annotations.RepositoryTypeAnnotation.class);
            if (annotation != null) {
                return annotation.value();
            }

            RepositoryType type = findTypeFromInterface(iface);
            if (type != null) {
                return type;
            }
        }
        return null;
    }

    private RepositoryType matchBySuperclassType(Class<?> superclass) {
        String className = superclass.getName();
        if (className.contains("JpaRepositoryDelegate")) {
            return RepositoryType.JPA;
        } else if (className.contains("MybatisPlusRepositoryDelegate")) {
            return RepositoryType.MYBATIS_PLUS;
        } else if (className.contains("MongoRepositoryDelegate")) {
            return RepositoryType.MONGODB;
        } else if (className.contains("ElasticsearchRepositoryDelegate")) {
            return RepositoryType.ELASTICSEARCH;
        }
        return null;
    }
}