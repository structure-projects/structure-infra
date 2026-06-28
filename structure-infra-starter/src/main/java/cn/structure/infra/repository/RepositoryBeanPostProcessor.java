package cn.structure.infra.repository;

import cn.structure.infra.annotations.DelegateFor;
import cn.structure.infra.annotations.Repository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
public class RepositoryBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware, ApplicationListener<ContextRefreshedEvent> {

    private ApplicationContext applicationContext;

    private final List<DelegateInfo> delegateInfos = new ArrayList<>();

    private final List<RepositoryFacadeInfo> facadeInfos = new ArrayList<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        DelegateFor annotation = bean.getClass().getAnnotation(DelegateFor.class);
        if (annotation != null) {
            DelegateInfo info = new DelegateInfo();
            info.beanName = beanName;
            info.name = annotation.name();
            info.type = annotation.type();
            info.poClass = annotation.po();
            info.priority = annotation.priority();
            info.description = annotation.description();
            info.delegateClass = bean.getClass();
            info.delegateType = annotation.delegateType();

            if (bean instanceof RepositoryDelegate) {
                info.delegate = (RepositoryDelegate<?, ?>) bean;
                log.info("Found RepositoryDelegate: name={}, type={}, delegateType={}, poClass={}, delegateClass={}, priority={}",
                        info.name, info.type, info.delegateType,
                        info.poClass != null ? info.poClass.getSimpleName() : "null",
                        info.delegateClass.getSimpleName(),
                        info.priority);
            }
            if (bean instanceof IQueryDelegate) {
                info.queryDelegate = (IQueryDelegate<?, ?>) bean;
                log.info("Found IQueryDelegate: name={}, type={}, delegateType={}, poClass={}, delegateClass={}, priority={}",
                        info.name, info.type, info.delegateType,
                        info.poClass != null ? info.poClass.getSimpleName() : "null",
                        info.delegateClass.getSimpleName(),
                        info.priority);
            }
            delegateInfos.add(info);
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof RepositoryFacade) {
            RepositoryFacadeInfo info = new RepositoryFacadeInfo();
            info.facade = (RepositoryFacade<?, ?, ?, ?>) bean;
            info.beanName = beanName;
            facadeInfos.add(info);
        }
        return bean;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        for (RepositoryFacadeInfo facadeInfo : facadeInfos) {
            injectDelegatesToFacade(facadeInfo.facade, facadeInfo.beanName);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void injectDelegatesToFacade(RepositoryFacade facade, String beanName) {
        try {
            Class<?>[] genericTypes = getGenericTypes(facade.getClass());
            if (genericTypes.length < 4) {
                log.debug("RepositoryFacade '{}' has insufficient generic types (need 4, got {})", beanName, genericTypes.length);
                return;
            }

            Class<?> entityClass = genericTypes[0];
            Class<?> idClass = genericTypes[1];
            Class<?> poClass = genericTypes[2];
            Class<?> delegateClass = genericTypes[3];

            Repository repositoryAnnotation = facade.getClass().getAnnotation(Repository.class);
            RepositoryType targetType = repositoryAnnotation != null ? repositoryAnnotation.type() : RepositoryType.AUTO;
            boolean cqrsEnabled = repositoryAnnotation != null && repositoryAnnotation.cqrs();
            Class<?> readDelegateClass = repositoryAnnotation != null ? repositoryAnnotation.readDelegateClass() : Object.class;

            log.debug("RepositoryFacade '{}' requires: entity={}, id={}, po={}, delegateClass={}, type={}, cqrs={}, readDelegateClass={}",
                    beanName, entityClass.getSimpleName(), idClass.getSimpleName(),
                    poClass.getSimpleName(), delegateClass.getSimpleName(), targetType, cqrsEnabled,
                    readDelegateClass != null ? readDelegateClass.getSimpleName() : "null");

            DelegateInfo baseDelegateInfo = findBaseDelegate(beanName, poClass, delegateClass, targetType);

            if (baseDelegateInfo != null && baseDelegateInfo.delegate != null) {
                facade.setBaseDelegate(baseDelegateInfo.delegate);
                log.info("Injected BASE delegate '{}' (type={}) into RepositoryFacade '{}'",
                        baseDelegateInfo.beanName, baseDelegateInfo.type, beanName);
            } else {
                log.debug("No matching BASE delegate found for RepositoryFacade '{}', trying to auto-create delegate via factory", beanName);
                RepositoryDelegate autoDelegate = autoCreateDelegate(poClass, idClass, targetType);

                if (autoDelegate != null) {
                    facade.setBaseDelegate(autoDelegate);
                    log.info("Auto-created BASE delegate (type={}) for RepositoryFacade '{}'", targetType, beanName);
                } else {
                    log.warn("No matching BASE delegate found for RepositoryFacade '{}', using default InMemoryRepositoryDelegate", beanName);
                    RepositoryDelegate defaultDelegate = new InMemoryRepositoryDelegate(poClass);
                    facade.setBaseDelegate(defaultDelegate);
                }
            }

            boolean shouldEnableReadDelegate = cqrsEnabled && readDelegateClass != null && readDelegateClass != Object.class;
            if (shouldEnableReadDelegate) {
                // READ 代理使用 AUTO 类型匹配，因为 CQRS 模式下读代理可能与写代理类型不同
                // 例如：写代理用 MyBatis Plus，读代理用 Elasticsearch
                DelegateInfo readDelegateInfo = findReadDelegate(beanName, poClass, readDelegateClass, RepositoryType.AUTO);

                if (readDelegateInfo != null && readDelegateInfo.queryDelegate != null) {
                    facade.setReadDelegate(readDelegateInfo.queryDelegate);
                    log.info("Injected READ delegate '{}' (type={}) into RepositoryFacade '{}'",
                            readDelegateInfo.beanName, readDelegateInfo.type, beanName);
                } else {
                    log.warn("No matching READ delegate found for RepositoryFacade '{}' (readDelegateClass={}), read operations will use BASE delegate",
                            beanName, readDelegateClass.getSimpleName());
                }
            }

            facade.setEntityClass(entityClass);
            facade.setPoClass(poClass);

        } catch (Exception e) {
            log.warn("Error injecting delegates to RepositoryFacade '{}': {}", beanName, e.getMessage());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private RepositoryDelegate autoCreateDelegate(Class<?> poClass, Class<?> idClass, RepositoryType targetType) {
        try {
            Map<String, RepositoryDelegateFactory> factories = applicationContext.getBeansOfType(RepositoryDelegateFactory.class);

            if (factories.isEmpty()) {
                log.debug("No RepositoryDelegateFactory beans found in application context");
                return null;
            }

            if (targetType != RepositoryType.AUTO) {
                for (RepositoryDelegateFactory factory : factories.values()) {
                    if (factory.getType() == targetType) {
                        RepositoryDelegate delegate = factory.createDelegate(poClass, idClass);
                        if (delegate != null) {
                            return delegate;
                        }
                    }
                }
            } else {
                for (RepositoryDelegateFactory factory : factories.values()) {
                    try {
                        RepositoryDelegate delegate = factory.createDelegate(poClass, idClass);
                        if (delegate != null) {
                            log.debug("Auto-created delegate using factory for type: {}", factory.getType());
                            return delegate;
                        }
                    } catch (Exception e) {
                        log.debug("Factory {} failed to create delegate: {}", factory.getType(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to auto-create delegate: {}", e.getMessage());
        }
        return null;
    }

    private DelegateInfo findBaseDelegate(String beanName, Class<?> poClass, Class<?> delegateClass, RepositoryType targetType) {
        List<DelegateInfo> filtered = delegateInfos.stream()
                .filter(info -> info.delegateType == DelegateType.BASE && info.delegate != null)
                .sorted(Comparator.comparingInt((DelegateInfo i) -> i.priority).reversed())
                .toList();

        for (DelegateInfo info : filtered) {
            if (delegateClass.isAssignableFrom(info.delegateClass)
                    && info.name != null && !info.name.isEmpty() && info.name.equals(beanName)
                    && (targetType == RepositoryType.AUTO || targetType == info.type)) {
                return info;
            }
        }

        for (DelegateInfo info : filtered) {
            if (delegateClass.isAssignableFrom(info.delegateClass)
                    && (targetType == RepositoryType.AUTO || targetType == info.type)) {
                return info;
            }
        }

        for (DelegateInfo info : filtered) {
            if (delegateClass.isAssignableFrom(info.delegateClass)
                    && info.name != null && !info.name.isEmpty() && info.name.equals(beanName)) {
                return info;
            }
        }

        for (DelegateInfo info : filtered) {
            if (delegateClass.isAssignableFrom(info.delegateClass)) {
                return info;
            }
        }

        for (DelegateInfo info : filtered) {
            if (info.name != null && !info.name.isEmpty() && info.name.equals(beanName)) {
                return info;
            }
        }

        for (DelegateInfo info : filtered) {
            if (info.poClass != null && info.poClass.equals(poClass)) {
                return info;
            }
        }

        return null;
    }

    private DelegateInfo findReadDelegate(String beanName, Class<?> poClass, Class<?> readDelegateClass, RepositoryType targetType) {
        List<DelegateInfo> filtered = delegateInfos.stream()
                .filter(info -> info.delegateType == DelegateType.READ && info.queryDelegate != null)
                .sorted(Comparator.comparingInt((DelegateInfo i) -> i.priority).reversed())
                .toList();

        for (DelegateInfo info : filtered) {
            if (readDelegateClass.isAssignableFrom(info.delegateClass)
                    && info.name != null && !info.name.isEmpty() && info.name.equals(beanName)
                    && (targetType == RepositoryType.AUTO || targetType == info.type)) {
                return info;
            }
        }

        for (DelegateInfo info : filtered) {
            if (readDelegateClass.isAssignableFrom(info.delegateClass)
                    && (targetType == RepositoryType.AUTO || targetType == info.type)) {
                return info;
            }
        }

        for (DelegateInfo info : filtered) {
            if (readDelegateClass.isAssignableFrom(info.delegateClass)
                    && info.name != null && !info.name.isEmpty() && info.name.equals(beanName)) {
                return info;
            }
        }

        for (DelegateInfo info : filtered) {
            if (readDelegateClass.isAssignableFrom(info.delegateClass)) {
                return info;
            }
        }

        for (DelegateInfo info : filtered) {
            if (info.name != null && !info.name.isEmpty() && info.name.equals(beanName)) {
                return info;
            }
        }

        for (DelegateInfo info : filtered) {
            if (info.poClass != null && info.poClass.equals(poClass)) {
                return info;
            }
        }

        return null;
    }

    private Class<?>[] getGenericTypes(Class<?> clazz) {
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            Type superclass = currentClass.getGenericSuperclass();
            if (superclass instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) superclass;
                Type rawType = parameterizedType.getRawType();
                if (rawType instanceof Class && RepositoryFacade.class.isAssignableFrom((Class<?>) rawType)) {
                    Type[] typeArgs = parameterizedType.getActualTypeArguments();
                    Class<?>[] classes = new Class[typeArgs.length];
                    for (int i = 0; i < typeArgs.length; i++) {
                        if (typeArgs[i] instanceof Class) {
                            classes[i] = (Class<?>) typeArgs[i];
                        } else if (typeArgs[i] instanceof ParameterizedType) {
                            Type raw = ((ParameterizedType) typeArgs[i]).getRawType();
                            if (raw instanceof Class) {
                                classes[i] = (Class<?>) raw;
                            }
                        }
                    }
                    return classes;
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return new Class<?>[0];
    }

    private static class DelegateInfo {
        String beanName;
        String name;
        RepositoryType type;
        Class<?> poClass;
        int priority;
        String description;
        Class<?> delegateClass;
        RepositoryDelegate<?, ?> delegate;
        IQueryDelegate<?, ?> queryDelegate;
        DelegateType delegateType;
    }

    private static class RepositoryFacadeInfo {
        RepositoryFacade<?, ?, ?, ?> facade;
        String beanName;
    }
}