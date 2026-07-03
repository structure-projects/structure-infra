package cn.structure.infra.stream.configuration;

import cn.structure.infra.stream.manager.DefaultStreamEventManagerImpl;
import cn.structure.infra.stream.manager.StreamEventManager;
import cn.structure.infra.stream.properties.StreamProperties;
import cn.structure.infra.stream.processor.EventListenerBeanPostProcessor;
import cn.structure.infra.stream.processor.StreamBindingBeanFactoryPostProcessor;
import cn.structure.infra.stream.router.DefaultStreamEventRouterImpl;
import cn.structure.infra.stream.router.RouterProperties;
import cn.structure.infra.stream.router.StreamEventRouter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;

/**
 * stream 模块的自动配置类，注册所有核心 Bean。
 *
 * <p>设计意图：
 * <ul>
 *   <li>作为 Spring Boot 自动配置入口，统一装配 stream 模块所需的核心组件</li>
 *   <li>通过 {@code @ConditionalOnClass(StreamBridge.class)} 保证仅当 Spring Cloud Stream 在类路径时才激活</li>
 *   <li>通过 {@code @ConditionalOnProperty} 提供 enabled 全局开关，默认启用（matchIfMissing = true）</li>
 *   <li>通过 {@code @EnableConfigurationProperties} 同时启用 {@link StreamProperties} 与 {@link RouterProperties}</li>
 * </ul>
 *
 * <p>注册的 Bean：
 * <ul>
 *   <li>{@link StreamEventManager}：事件管理器（默认实现 {@link DefaultStreamEventManagerImpl}）</li>
 *   <li>{@link StreamEventRouter}：路由网关（默认实现 {@link DefaultStreamEventRouterImpl}）</li>
 *   <li>{@link StreamBindingBeanFactoryPostProcessor}：BeanFactory 阶段的自动 binding 注册器（static）</li>
 *   <li>{@link EventListenerBeanPostProcessor}：Bean 阶段的自动监听器注册器</li>
 * </ul>
 *
 * <p>注意：{@link cn.structure.infra.stream.router.RouteHandlerBeanPostProcessor} 与
 * {@link cn.structure.infra.stream.router.ConfigurableRouteInitializer} 通过自身 {@code @Component}
 * 注解由组件扫描自动注册，故本配置类不再重复声明。
 *
 * @see StreamProperties
 * @see RouterProperties
 */
@AutoConfiguration
@ConditionalOnClass({StreamBridge.class})
@ConditionalOnProperty(prefix = "structure.infra.stream", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({StreamProperties.class, RouterProperties.class})
public class StreamAutoConfiguration {

    /**
     * 注册事件管理器 Bean，默认实现为 {@link DefaultStreamEventManagerImpl}。
     * <p>当容器中不存在 {@link StreamEventManager} 时才创建，允许用户自定义覆盖。
     *
     * @param streamBridge     Spring Cloud Stream 桥接器
     * @param streamProperties stream 主配置
     * @return 事件管理器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public StreamEventManager streamEventManager(StreamBridge streamBridge,
                                                  StreamProperties streamProperties) {
        return new DefaultStreamEventManagerImpl(streamBridge, streamProperties);
    }

    /**
     * 注册路由网关 Bean，默认实现为 {@link DefaultStreamEventRouterImpl}。
     * <p>当容器中不存在 {@link StreamEventRouter} 时才创建，允许用户自定义覆盖。
     *
     * @return 路由网关实例
     */
    @Bean
    @ConditionalOnMissingBean
    public StreamEventRouter streamEventRouter() {
        return new DefaultStreamEventRouterImpl();
    }

    /**
     * 注册 {@link StreamBindingBeanFactoryPostProcessor} Bean，用于在 BeanFactory 阶段扫描注解并自动注册 binding。
     * <p>声明为 static 是为了保证其在所有 Bean 实例化之前执行，避免提前触发 Bean 创建。
     *
     * @return BeanFactory 后置处理器实例
     */
    @Bean
    public static StreamBindingBeanFactoryPostProcessor streamBindingBeanFactoryPostProcessor() {
        return new StreamBindingBeanFactoryPostProcessor();
    }

    /**
     * 注册 {@link EventListenerBeanPostProcessor} Bean，用于在 Bean 初始化阶段扫描 {@code @StreamEventListener} 注解并注册监听器。
     *
     * @param streamEventManager 事件管理器 SPI
     * @param streamProperties   stream 主配置
     * @return Bean 后置处理器实例
     */
    @Bean
    public EventListenerBeanPostProcessor eventListenerBeanPostProcessor(StreamEventManager streamEventManager,
                                                                          StreamProperties streamProperties) {
        return new EventListenerBeanPostProcessor(streamEventManager, streamProperties);
    }

}
