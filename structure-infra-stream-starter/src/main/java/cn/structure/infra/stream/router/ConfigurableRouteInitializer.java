package cn.structure.infra.stream.router;

import cn.structure.infra.stream.event.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
public class ConfigurableRouteInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableRouteInitializer.class);

    private final RouterProperties routerProperties;
    private final StreamEventRouter eventRouter;
    private final ApplicationContext applicationContext;

    public ConfigurableRouteInitializer(RouterProperties routerProperties, 
                                        StreamEventRouter eventRouter,
                                        ApplicationContext applicationContext) {
        this.routerProperties = routerProperties;
        this.eventRouter = eventRouter;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) {
        if (!routerProperties.isEnabled()) {
            log.info("Configurable router is disabled");
            return;
        }

        log.info("========== 配置驱动路由初始化 ==========");

        for (RouterProperties.RouteDefinition route : routerProperties.getRoutes()) {
            try {
                registerRoute(route);
                log.info("Registered route: id={}, eventType={}, handler={}.{}",
                        route.getId(), route.getEventType(), route.getHandlerBean(), route.getHandlerMethod());
            } catch (Exception e) {
                log.error("Failed to register route: id={}, eventType={}", route.getId(), route.getEventType(), e);
            }
        }

        log.info("========== 配置驱动路由初始化完成，共 {} 条路由 ==========", routerProperties.getRoutes().size());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerRoute(RouterProperties.RouteDefinition route) throws Exception {
        Class<?> payloadType = Class.forName(route.getPayloadType());
        Object handlerBean = applicationContext.getBean(route.getHandlerBean());
        Method handlerMethod = handlerBean.getClass().getDeclaredMethod(route.getHandlerMethod(), payloadType);
        handlerMethod.setAccessible(true);

        StreamEventRouter.StreamRouteHandler handler = (payload, event) -> {
            try {
                handlerMethod.invoke(handlerBean, payload);
            } catch (Exception e) {
                log.error("Error invoking handler: {}.{}", route.getHandlerBean(), route.getHandlerMethod(), e);
                throw new RuntimeException("Error invoking handler", e);
            }
        };

        if (route.getBusinessType() != null && !route.getBusinessType().isEmpty()) {
            if (route.getCondition() != null && !route.getCondition().isEmpty()) {
                eventRouter.registerRoute(route.getEventType(), route.getBusinessType(), payloadType, route.getCondition(), handler);
            } else {
                eventRouter.registerRoute(route.getEventType(), route.getBusinessType(), payloadType, handler);
            }
        } else {
            if (route.getCondition() != null && !route.getCondition().isEmpty()) {
                eventRouter.registerRoute(route.getEventType(), payloadType, route.getCondition(), handler);
            } else {
                eventRouter.registerRoute(route.getEventType(), payloadType, handler);
            }
        }
    }
}
