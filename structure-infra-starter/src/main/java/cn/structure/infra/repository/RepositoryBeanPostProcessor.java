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
        if (bean instanceof RepositoryDelegate) {
            DelegateFor annotation = bean.getClass().getAnnotation(DelegateFor.class);
            if (annotation != null) {
                DelegateInfo info = new DelegateInfo();
                info.beanName = beanName;
                info.delegate = (RepositoryDelegate<?, ?>) bean;
                info.name = annotation.name();
                info.type = annotation.type();
                info.poClass = annotation.po();
                info.priority = annotation.priority();
                info.description = annotation.description();
                info.delegateClass = bean.getClass();
                delegateInfos.add(info);
                log.info("Found RepositoryDelegate: name={}, type={}, poClass={}, delegateClass={}, priority={}",
                        info.name, info.type,
                        info.poClass != null ? info.poClass.getSimpleName() : "null",
                        info.delegateClass.getSimpleName(),
                        info.priority);
            }
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
            injectDelegateToFacade(facadeInfo.facade, facadeInfo.beanName);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void injectDelegateToFacade(RepositoryFacade facade, String beanName) {
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

            log.debug("RepositoryFacade '{}' requires: entity={}, id={}, po={}, delegateClass={}, type={}",
                    beanName, entityClass.getSimpleName(), idClass.getSimpleName(),
                    poClass.getSimpleName(), delegateClass.getSimpleName(), targetType);

            DelegateInfo matchedInfo = findBestMatch(beanName, poClass, delegateClass, targetType);

            if (matchedInfo != null) {
                facade.setBaseDelegate(matchedInfo.delegate);
                log.info("Injected {} delegate '{}' into RepositoryFacade '{}'",
                        matchedInfo.type, matchedInfo.beanName, beanName);
            } else {
                log.warn("No matching delegate found for RepositoryFacade '{}', using default InMemoryRepositoryDelegate", beanName);
                RepositoryDelegate defaultDelegate = new InMemoryRepositoryDelegate(poClass);
                facade.setBaseDelegate(defaultDelegate);
            }

            facade.setEntityClass(entityClass);
            facade.setPoClass(poClass);

        } catch (Exception e) {
            log.warn("Error injecting delegate to RepositoryFacade '{}': {}", beanName, e.getMessage());
        }
    }

    private DelegateInfo findBestMatch(String beanName, Class<?> poClass, Class<?> delegateClass, RepositoryType targetType) {
        delegateInfos.sort(Comparator.comparingInt((DelegateInfo i) -> i.priority).reversed());

        for (DelegateInfo info : delegateInfos) {
            if (delegateClass.isAssignableFrom(info.delegateClass)
                    && info.name != null && !info.name.isEmpty() && info.name.equals(beanName)
                    && (targetType == RepositoryType.AUTO || targetType == info.type)) {
                return info;
            }
        }

        for (DelegateInfo info : delegateInfos) {
            if (delegateClass.isAssignableFrom(info.delegateClass)
                    && (targetType == RepositoryType.AUTO || targetType == info.type)) {
                return info;
            }
        }

        for (DelegateInfo info : delegateInfos) {
            if (delegateClass.isAssignableFrom(info.delegateClass)
                    && info.name != null && !info.name.isEmpty() && info.name.equals(beanName)) {
                return info;
            }
        }

        for (DelegateInfo info : delegateInfos) {
            if (delegateClass.isAssignableFrom(info.delegateClass)) {
                return info;
            }
        }

        for (DelegateInfo info : delegateInfos) {
            if (info.name != null && !info.name.isEmpty() && info.name.equals(beanName)) {
                return info;
            }
        }

        for (DelegateInfo info : delegateInfos) {
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
    }

    private static class RepositoryFacadeInfo {
        RepositoryFacade<?, ?, ?, ?> facade;
        String beanName;
    }
}