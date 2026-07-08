package cn.structure.infra.stream.router;

import cn.structure.infra.stream.event.StreamEvent;

import java.util.List;

/**
 * 路由网关 SPI，根据 eventType/businessType/payloadType/condition 将 {@link StreamEvent} 路由到匹配的处理器。
 *
 * <p>设计意图：
 * <ul>
 *   <li>提供面向事件类型的统一路由入口，与 Spring Cloud Stream 的 binding 模型解耦</li>
 *   <li>支持 4 步路由匹配规则：eventType 精确匹配 → businessType 通配匹配 → payloadType 类型匹配 → SpEL condition 条件求值</li>
 *   <li>支持运行时动态注册/注销路由，便于扩展</li>
 * </ul>
 *
 * <p>协作关系：
 * <ul>
 *   <li>由 {@link DefaultStreamEventRouterImpl} 提供默认实现</li>
 *   <li>由 {@link RouteHandlerBeanPostProcessor} 扫描 {@code @StreamRouteHandler} 注解方法自动注册</li>
 *   <li>由 {@link ConfigurableRouteInitializer} 根据 YAML 配置批量注册路由</li>
 * </ul>
 *
 * @see DefaultStreamEventRouterImpl
 * @see RouteRegistration
 * @see StreamRouteHandler
 */
public interface StreamEventRouter {

    /**
     * 注册路由，仅指定 eventType 与 payloadType，businessType 与 condition 留空。
     *
     * @param eventType   事件类型
     * @param payloadType 负载类型
     * @param handler     路由处理器
     * @param <T>         负载类型
     */
    <T> void registerRoute(String eventType, Class<T> payloadType, StreamRouteHandler<T> handler);

    /**
     * 注册路由，指定 eventType、payloadType 与 SpEL condition，businessType 留空。
     *
     * @param eventType   事件类型
     * @param payloadType 负载类型
     * @param condition   SpEL 条件表达式，可通过 {@code #payload} 引用负载
     * @param handler     路由处理器
     * @param <T>         负载类型
     */
    <T> void registerRoute(String eventType, Class<T> payloadType, String condition, StreamRouteHandler<T> handler);

    /**
     * 注册路由，指定 eventType、businessType 与 payloadType，condition 留空。
     *
     * @param eventType    事件类型
     * @param businessType 业务类型，支持 {@code "*"} 通配符
     * @param payloadType  负载类型
     * @param handler      路由处理器
     * @param <T>          负载类型
     */
    <T> void registerRoute(String eventType, String businessType, Class<T> payloadType, StreamRouteHandler<T> handler);

    /**
     * 注册路由，指定全部四个匹配维度。
     *
     * @param eventType    事件类型
     * @param businessType 业务类型，支持 {@code "*"} 通配符
     * @param payloadType  负载类型
     * @param condition    SpEL 条件表达式
     * @param handler      路由处理器
     * @param <T>          负载类型
     */
    <T> void registerRoute(String eventType, String businessType, Class<T> payloadType, String condition, StreamRouteHandler<T> handler);

    /**
     * 注销指定 eventType 下的全部路由。
     *
     * @param eventType 事件类型
     */
    void unregisterRoute(String eventType);

    /**
     * 按 handlerId 精确注销路由。
     *
     * @param eventType 事件类型
     * @param handlerId 路由唯一 ID
     */
    void unregisterRoute(String eventType, String handlerId);

    /**
     * 路由事件，按 4 步规则匹配后回调处理器。
     *
     * @param event 事件信封
     * @param <T>   负载类型
     */
    <T> void route(StreamEvent<T> event);

    /**
     * 判断指定 eventType 是否存在已注册的路由。
     *
     * @param eventType 事件类型
     * @return 存在且非空返回 true
     */
    boolean isRouteRegistered(String eventType);

    /**
     * @param eventType 事件类型
     * @return 该 eventType 下的所有路由注册信息，不存在时返回空列表
     */
    List<RouteRegistration<?>> getRoutes(String eventType);

    /**
     * 路由处理器函数接口，与 {@link cn.structure.infra.stream.handler.StreamEventHandler} 区分：
     * 本接口同时接收 payload 与完整事件信封，便于处理器在需要时访问路由元数据。
     *
     * @param <T> 负载类型
     */
    interface StreamRouteHandler<T> {
        /**
         * 处理路由事件。
         *
         * @param payload 业务负载，已通过 payloadType 类型匹配
         * @param event   完整事件信封，可访问 eventType/businessType/headers 等
         */
        void handle(T payload, StreamEvent<T> event);
    }

}
