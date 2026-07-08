package cn.structure.infra.stream.router;

import cn.structure.infra.stream.event.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 配置驱动路由初始化器，在应用启动时读取 {@link RouterProperties} 并批量注册路由。
 *
 * <p>设计意图：
 * <ul>
 *   <li>作为 {@link cn.structure.infra.stream.annotation.StreamRouteHandler} 注解的补充，
 *       提供纯 YAML 配置的路由注册能力，无需修改 Java 代码</li>
 *   <li>通过 Spring {@link CommandLineRunner} 在所有 Bean 初始化完成后执行</li>
 *   <li>反射加载 payloadType、查找 handlerBean、定位 handlerMethod，包装为
 *       {@link StreamEventRouter.StreamRouteHandler} 注册到 {@link StreamEventRouter}</li>
 * </ul>
 *
 * <p>协作关系：
 * <ul>
 *   <li>依赖 {@link RouterProperties} 提供路由定义列表</li>
 *   <li>依赖 {@link StreamEventRouter} 完成实际注册</li>
 *   <li>依赖 {@link ApplicationContext} 通过 beanName 查找处理器 Bean</li>
 * </ul>
 *
 * @see RouterProperties
 * @see StreamEventRouter
 */
@Component
public class ConfigurableRouteInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableRouteInitializer.class);

    /**
     * 路由配置属性。
     */
    private final RouterProperties routerProperties;
    /**
     * 路由器 SPI，用于实际注册路由。
     */
    private final StreamEventRouter eventRouter;
    /**
     * Spring 应用上下文，用于按 beanName 查找处理器 Bean。
     */
    private final ApplicationContext applicationContext;

    /**
     * 构造方法，由 Spring 注入依赖。
     *
     * @param routerProperties    路由配置属性
     * @param eventRouter         路由器 SPI
     * @param applicationContext  Spring 应用上下文
     */
    public ConfigurableRouteInitializer(RouterProperties routerProperties,
                                        StreamEventRouter eventRouter,
                                        ApplicationContext applicationContext) {
        this.routerProperties = routerProperties;
        this.eventRouter = eventRouter;
        this.applicationContext = applicationContext;
    }

    /**
     * 应用启动入口：迭代 {@link RouterProperties#getRoutes()} 批量注册路由。
     *
     * <p>实现说明：
     * <ol>
     *   <li>若 {@link RouterProperties#isEnabled()} 为 false，跳过初始化</li>
     *   <li>逐条调用 {@link #registerRoute} 注册，单条失败不影响其他路由</li>
     *   <li>打印注册总数统计日志</li>
     * </ol>
     *
     * @param args 启动参数（未使用）
     */
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
                // 单条路由注册失败不影响其他路由
                log.error("Failed to register route: id={}, eventType={}", route.getId(), route.getEventType(), e);
            }
        }

        log.info("========== 配置驱动路由初始化完成，共 {} 条路由 ==========", routerProperties.getRoutes().size());
    }

    /**
     * 注册单条配置驱动路由。
     *
     * <p>实现说明：
     * <ol>
     *   <li>反射加载 payloadType 全限定类名</li>
     *   <li>通过 {@link ApplicationContext#getBean(String)} 查找 handlerBean</li>
     *   <li>反射定位 handlerMethod（参数类型为 payloadType），设置 accessible</li>
     *   <li>包装为 {@link StreamEventRouter.StreamRouteHandler} Lambda</li>
     *   <li>根据 businessType/condition 是否非空，选择四参/三参/双参版本注册</li>
     * </ol>
     *
     * @param route 路由定义
     * @throws Exception 当 payloadType 类加载失败、bean 查找失败或方法定位失败时抛出
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerRoute(RouterProperties.RouteDefinition route) throws Exception {
        // 反射加载 payloadType
        Class<?> payloadType = Class.forName(route.getPayloadType());
        // 按 beanName 查找处理器 Bean
        Object handlerBean = applicationContext.getBean(route.getHandlerBean());
        // 反射定位处理器方法（参数类型为 payloadType）
        Method handlerMethod = handlerBean.getClass().getDeclaredMethod(route.getHandlerMethod(), payloadType);
        handlerMethod.setAccessible(true);

        // 包装为 StreamRouteHandler Lambda，反射调用 handlerMethod
        StreamEventRouter.StreamRouteHandler handler = (payload, event) -> {
            try {
                handlerMethod.invoke(handlerBean, payload);
            } catch (Exception e) {
                log.error("Error invoking handler: {}.{}", route.getHandlerBean(), route.getHandlerMethod(), e);
                throw new RuntimeException("Error invoking handler", e);
            }
        };

        // 根据 businessType/condition 是否非空，选择对应的注册重载版本
        if (route.getBusinessType() != null && !route.getBusinessType().isEmpty()) {
            if (route.getCondition() != null && !route.getCondition().isEmpty()) {
                // businessType 与 condition 均非空：四参版本
                eventRouter.registerRoute(route.getEventType(), route.getBusinessType(), payloadType, route.getCondition(), handler);
            } else {
                // 仅 businessType 非空
                eventRouter.registerRoute(route.getEventType(), route.getBusinessType(), payloadType, handler);
            }
        } else {
            if (route.getCondition() != null && !route.getCondition().isEmpty()) {
                // 仅 condition 非空
                eventRouter.registerRoute(route.getEventType(), payloadType, route.getCondition(), handler);
            } else {
                // businessType 与 condition 均空：双参版本
                eventRouter.registerRoute(route.getEventType(), payloadType, handler);
            }
        }
    }
}
