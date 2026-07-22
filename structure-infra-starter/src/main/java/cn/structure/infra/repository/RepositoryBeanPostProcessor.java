package cn.structure.infra.repository;

import cn.structure.infra.annotations.ReadDelegate;
import cn.structure.infra.annotations.WriteDelegate;
import cn.structure.infra.properties.InfraProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class RepositoryBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    private DefaultListableBeanFactory beanFactory;

    private InfraProperties infraProperties;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.beanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        this.infraProperties = applicationContext.getBean(InfraProperties.class);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (Boolean.TRUE.equals(infraProperties.getMultiRepositoryEnabled())) {
            return bean;
        }
        // 只处理 CQRS 场景，且只注入 readDelegate
        if (bean instanceof CqrsRepositoryFacade) {
            processCqrsReadDelegate((CqrsRepositoryFacade<?, ?, ?, ?>) bean, beanName);
        }
        return bean;
    }

    private void processCqrsReadDelegate(CqrsRepositoryFacade<?, ?, ?, ?> facade, String beanName) {
        Type[] typeArgs = resolveTypeArguments(facade.getClass(), CqrsRepositoryFacade.class);
        if (typeArgs == null || typeArgs.length < 4) {
            log.warn("Cannot resolve generic types for CqrsRepositoryFacade: {}", beanName);
            return;
        }

        Class<?> readDelegateType = getRawType(typeArgs[3]);
        if (readDelegateType != null) {
            Object readDelegate = findReadDelegate(readDelegateType);
            if (readDelegate != null) {
                setDelegate(facade, "readDelegate", readDelegate);
                log.info("Injected read delegate [{}] to CqrsRepositoryFacade [{}]",
                        readDelegate.getClass().getSimpleName(), beanName);
            }
        }
    }

    private Object findWriteDelegate(Class<?> delegateType) {
        Map<String, ?> beans = beanFactory.getBeansOfType(delegateType);
        if (beans.isEmpty()) {
            return null;
        }

        List<Object> candidates = new ArrayList<>(beans.values());

        Object writeDelegate = findByAnnotation(candidates, WriteDelegate.class);
        if (writeDelegate != null) {
            return writeDelegate;
        }

        Object noReadDelegate = findWithoutAnnotation(candidates, ReadDelegate.class);
        if (noReadDelegate != null) {
            return noReadDelegate;
        }

        return candidates.get(0);
    }

    private Object findReadDelegate(Class<?> delegateType) {
        Map<String, ?> beans = beanFactory.getBeansOfType(delegateType);
        if (beans.isEmpty()) {
            return null;
        }

        List<Object> candidates = new ArrayList<>(beans.values());
        
        Object readDelegate = findByAnnotation(candidates, ReadDelegate.class);
        if (readDelegate != null) {
            return readDelegate;
        }

        return candidates.get(0);
    }

    private <A extends java.lang.annotation.Annotation> Object findByAnnotation(List<Object> candidates, Class<A> annotationClass) {
        for (Object candidate : candidates) {
            if (AnnotationUtils.findAnnotation(candidate.getClass(), annotationClass) != null) {
                return candidate;
            }
        }
        return null;
    }

    private <A extends java.lang.annotation.Annotation> Object findWithoutAnnotation(List<Object> candidates, Class<A> annotationClass) {
        for (Object candidate : candidates) {
            if (AnnotationUtils.findAnnotation(candidate.getClass(), annotationClass) == null) {
                return candidate;
            }
        }
        return null;
    }

    private void setDelegate(Object facade, String fieldName, Object delegate) {
        try {
            Field field = findField(facade.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.set(facade, delegate);
            }
        } catch (Exception e) {
            log.error("Failed to inject delegate to field [{}] in [{}]: {}",
                    fieldName, facade.getClass().getSimpleName(), e.getMessage());
        }
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
}