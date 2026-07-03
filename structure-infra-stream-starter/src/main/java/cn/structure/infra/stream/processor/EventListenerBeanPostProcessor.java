package cn.structure.infra.stream.processor;

import cn.structure.infra.stream.annotation.StreamEventListener;
import cn.structure.infra.stream.manager.StreamEventManager;
import cn.structure.infra.stream.properties.StreamProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.messaging.Message;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EventListenerBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(EventListenerBeanPostProcessor.class);

    private final StreamEventManager streamEventManager;
    private final StreamProperties streamProperties;

    private final Map<String, Object> listenerBeans = new ConcurrentHashMap<>();

    public EventListenerBeanPostProcessor(StreamEventManager streamEventManager, StreamProperties streamProperties) {
        this.streamEventManager = streamEventManager;
        this.streamProperties = streamProperties;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();
        Map<Method, StreamEventListener> annotatedMethods = MethodIntrospector.selectMethods(targetClass,
                (MethodIntrospector.MetadataLookup<StreamEventListener>) method ->
                        AnnotatedElementUtils.findMergedAnnotation(method, StreamEventListener.class));

        for (Map.Entry<Method, StreamEventListener> entry : annotatedMethods.entrySet()) {
            Method method = entry.getKey();
            StreamEventListener annotation = entry.getValue();
            registerListener(bean, method, annotation);
        }

        if (targetClass.isAnnotationPresent(StreamEventListener.class)) {
            StreamEventListener annotation = targetClass.getAnnotation(StreamEventListener.class);
            registerClassListener(bean, targetClass, annotation);
        }

        return bean;
    }

    private void registerListener(Object bean, Method method, StreamEventListener annotation) {
        String bindingName = resolveBindingName(annotation);
        String destination = annotation.destination();
        String group = annotation.group();
        Class<?> eventType = annotation.eventType();

        if (eventType == Object.class && method.getParameterTypes().length > 0) {
            eventType = method.getParameterTypes()[0];
        }

        if (bindingName.isEmpty()) {
            bindingName = method.getName();
        }

        // 确保绑定信息注册到 StreamProperties（供 publish 方法使用）
        ensureBindingRegistered(bindingName, destination, group, annotation.contentType());

        listenerBeans.put(bindingName, bean);

        if (!destination.isEmpty()) {
            streamEventManager.registerListener(bindingName, destination, group, eventType, event -> {
                try {
                    method.invoke(bean, event);
                } catch (Exception e) {
                    log.error("Failed to invoke listener method: {}", method.getName(), e);
                }
            });
        } else {
            streamEventManager.registerListener(bindingName, eventType, event -> {
                try {
                    method.invoke(bean, event);
                } catch (Exception e) {
                    log.error("Failed to invoke listener method: {}", method.getName(), e);
                }
            });
        }

        log.info("Registered listener method: {} for binding: {}", method.getName(), bindingName);
    }

    private void registerClassListener(Object bean, Class<?> targetClass, StreamEventListener annotation) {
        String bindingName = resolveBindingName(annotation);
        String destination = annotation.destination();
        String group = annotation.group();
        Class<?> eventType = annotation.eventType();

        if (bindingName.isEmpty()) {
            bindingName = targetClass.getSimpleName();
        }

        ensureBindingRegistered(bindingName, destination, group, annotation.contentType());

        listenerBeans.put(bindingName, bean);

        if (!destination.isEmpty()) {
            streamEventManager.registerListener(bindingName, destination, group, eventType, event -> {
                try {
                    Method handleMethod = targetClass.getMethod("handle", eventType);
                    handleMethod.invoke(bean, event);
                } catch (Exception e) {
                    log.error("Failed to invoke listener handle method on class: {}", targetClass.getName(), e);
                }
            });
        } else {
            streamEventManager.registerListener(bindingName, eventType, event -> {
                try {
                    Method handleMethod = targetClass.getMethod("handle", eventType);
                    handleMethod.invoke(bean, event);
                } catch (Exception e) {
                    log.error("Failed to invoke listener handle method on class: {}", targetClass.getName(), e);
                }
            });
        }

        log.info("Registered listener class: {} for binding: {}", targetClass.getName(), bindingName);
    }

    /**
     * 确保绑定信息注册到 StreamProperties，供 publish 方法使用
     */
    private void ensureBindingRegistered(String bindingName, String destination, String group, String contentType) {
        if (!streamProperties.getBindings().containsKey(bindingName)) {
            StreamProperties.Binding binding = new StreamProperties.Binding();
            if (StringUtils.hasText(destination)) {
                binding.setDestination(destination);
            } else {
                binding.setDestination(toDestination(bindingName));
            }
            binding.setGroup(StringUtils.hasText(group) ? group : streamProperties.getDefaultGroup());
            binding.setContentType(StringUtils.hasText(contentType) ? contentType : streamProperties.getDefaultContentType());
            streamProperties.getBindings().put(bindingName, binding);
        }
    }

    private String toDestination(String name) {
        return name.replace(".", "-").replace("_", "-").toLowerCase() + "-exchange";
    }

    private String resolveBindingName(StreamEventListener annotation) {
        String bindingName = annotation.bindingName();
        if (bindingName.isEmpty()) {
            bindingName = annotation.value();
        }
        return bindingName;
    }

}