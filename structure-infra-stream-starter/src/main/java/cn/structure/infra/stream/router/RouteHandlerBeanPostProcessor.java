package cn.structure.infra.stream.router;

import cn.structure.infra.stream.annotation.StreamRouteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * {@link StreamRouteHandler} 注解的扫描器与注册器，基于 Spring {@link BeanPostProcessor} 实现。
 *
 * <p>设计意图：
 * <ul>
 *   <li>在 Bean 初始化完成后扫描其方法上的 {@link StreamRouteHandler} 注解</li>
 *   <li>解析注解元数据（eventType/businessType/condition/payloadType），将方法包装为
 *       {@link StreamEventRouter.StreamRouteHandler} 并注册到 {@link StreamEventRouter}</li>
 *   <li>支持通过 {@link StreamRouteHandler#value()} 与 {@link StreamRouteHandler#eventType()} 两种方式声明 eventType</li>
 * </ul>
 *
 * <p>协作关系：
 * <ul>
 *   <li>依赖 {@link StreamEventRouter} SPI 完成实际注册</li>
 *   <li>注册时 payloadType 取自方法首个参数类型（payloadType 类型匹配的第 3 步依据）</li>
 *   <li>仅处理方法级注解，不处理类级注解（与 {@code EventListenerBeanPostProcessor} 不同）</li>
 * </ul>
 *
 * @see StreamRouteHandler
 * @see StreamEventRouter
 */
@Component
public class RouteHandlerBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(RouteHandlerBeanPostProcessor.class);

    /**
     * 路由器 SPI，用于实际注册路由。
     */
    private final StreamEventRouter eventRouter;

    /**
     * 构造方法，由 Spring 注入 {@link StreamEventRouter}。
     *
     * @param eventRouter 路由器实例
     */
    public RouteHandlerBeanPostProcessor(StreamEventRouter eventRouter) {
        this.eventRouter = eventRouter;
    }

    /**
     * 默认直接返回 Bean，不做任何处理。
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    /**
     * 在 Bean 初始化完成后扫描方法上的 {@link StreamRouteHandler} 注解并注册路由。
     *
     * <p>实现说明：
     * <ol>
     *   <li>迭代 Bean 类的所有 declared methods</li>
     *   <li>检测到 {@link StreamRouteHandler} 注解时，解析 eventType/businessType/condition</li>
     *   <li>eventType 必填，缺失时跳过并告警；方法必须至少有一个参数，否则跳过</li>
     *   <li>取方法首个参数类型作为 payloadType，调用 {@link #registerRoute} 完成注册</li>
     * </ol>
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        for (Method method : beanClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(StreamRouteHandler.class)) {
                StreamRouteHandler annotation = method.getAnnotation(StreamRouteHandler.class);
                // eventType 可通过 value() 或 eventType() 两种方式声明，优先取 eventType()
                String eventType = annotation.eventType();
                if (eventType.isEmpty()) {
                    eventType = annotation.value();
                }
                String businessType = annotation.businessType();
                String condition = annotation.condition();

                // eventType 必填校验，缺失时跳过当前方法
                if (eventType.isEmpty()) {
                    log.warn("Skipping method {} in bean {}: eventType is not specified", method.getName(), beanName);
                    continue;
                }

                Class<?>[] parameterTypes = method.getParameterTypes();
                // 方法必须至少有一个参数，作为 payloadType
                if (parameterTypes.length == 0) {
                    log.warn("Skipping method {} in bean {}: no parameters found", method.getName(), beanName);
                    continue;
                }

                // payloadType 取首个参数类型（路由第 3 步类型匹配依据）
                Class<?> payloadType = parameterTypes[0];

                registerRoute(eventType, businessType, payloadType, condition, bean, method);
            }
        }
        return bean;
    }

    /**
     * 将标注方法包装为 {@link StreamEventRouter.StreamRouteHandler} 并注册到路由器。
     *
     * @param eventType    事件类型
     * @param businessType 业务类型
     * @param payloadType  负载类型
     * @param condition    SpEL 条件表达式
     * @param bean         承载方法的 Bean 实例
     * @param method       标注方法
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerRoute(String eventType, String businessType, Class<?> payloadType,
                               String condition, Object bean, Method method) {
        try {
            method.setAccessible(true);
            // 将方法反射调用包装为 StreamRouteHandler Lambda
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
