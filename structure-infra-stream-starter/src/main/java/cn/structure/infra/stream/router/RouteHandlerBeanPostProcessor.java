package cn.structure.infra.stream.router;

import cn.structure.infra.stream.annotation.StreamRouteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
public class RouteHandlerBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(RouteHandlerBeanPostProcessor.class);

    private final StreamEventRouter eventRouter;

    public RouteHandlerBeanPostProcessor(StreamEventRouter eventRouter) {
        this.eventRouter = eventRouter;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        for (Method method : beanClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(StreamRouteHandler.class)) {
                StreamRouteHandler annotation = method.getAnnotation(StreamRouteHandler.class);
                String eventType = annotation.eventType();
                if (eventType.isEmpty()) {
                    eventType = annotation.value();
                }
                String businessType = annotation.businessType();
                String condition = annotation.condition();

                if (eventType.isEmpty()) {
                    log.warn("Skipping method {} in bean {}: eventType is not specified", method.getName(), beanName);
                    continue;
                }

                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 0) {
                    log.warn("Skipping method {} in bean {}: no parameters found", method.getName(), beanName);
                    continue;
                }

                Class<?> payloadType = parameterTypes[0];

                registerRoute(eventType, businessType, payloadType, condition, bean, method);
            }
        }
        return bean;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerRoute(String eventType, String businessType, Class<?> payloadType,
                               String condition, Object bean, Method method) {
        try {
            method.setAccessible(true);
            StreamEventRouter.StreamRouteHandler handler = (payload, event) -> {
                try {
                    method.invoke(bean, payload);
                } catch (Exception e) {
                    log.error("Error invoking handler method: {}", method.getName(), e);
                    throw new RuntimeException("Error invoking handler method", e);
                }
            };

            eventRouter.registerRoute(eventType, businessType, payloadType, condition, handler);
            log.info("Registered route handler: eventType={}, businessType={}, payloadType={}, method={}",
                    eventType, businessType, payloadType.getName(), method.getName());
        } catch (Exception e) {
            log.error("Failed to register route handler for method: {}", method.getName(), e);
        }
    }
}
