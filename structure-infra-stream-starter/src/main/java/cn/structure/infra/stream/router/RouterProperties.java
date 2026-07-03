package cn.structure.infra.stream.router;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 路由配置属性，对应 YAML 配置项 <code>structure.infra.stream.router</code>。
 *
 * <p>设计意图：
 * <ul>
 *   <li>提供配置驱动的路由注册能力，避免必须使用 {@link cn.structure.infra.stream.annotation.StreamRouteHandler} 注解</li>
 *   <li>每条路由由 {@link RouteDefinition} 描述，包含 id/eventType/businessType/payloadType/condition/handlerBean/handlerMethod</li>
 *   <li>由 {@link ConfigurableRouteInitializer} 在应用启动时读取并批量注册到 {@link StreamEventRouter}</li>
 * </ul>
 *
 * <p>协作关系：
 * <ul>
 *   <li>由 {@code StreamAutoConfiguration} 通过 {@code @EnableConfigurationProperties} 启用</li>
 *   <li>由 {@link ConfigurableRouteInitializer} 消费 routes 列表</li>
 * </ul>
 *
 * @see ConfigurableRouteInitializer
 * @see RouteDefinition
 */
@ConfigurationProperties(prefix = "structure.infra.stream.router")
public class RouterProperties {

    /**
     * 是否启用配置驱动路由，默认 true。
     */
    private boolean enabled = true;
    /**
     * 路由定义列表，每项对应一条 YAML 中声明的路由。
     */
    private List<RouteDefinition> routes = new ArrayList<>();

    /**
     * @return 是否启用配置驱动路由
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled 是否启用配置驱动路由
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return 路由定义列表
     */
    public List<RouteDefinition> getRoutes() {
        return routes;
    }

    /**
     * @param routes 路由定义列表
     */
    public void setRoutes(List<RouteDefinition> routes) {
        this.routes = routes;
    }

    /**
     * 单条路由定义，描述一条配置驱动的路由规则与处理器位置。
     *
     * <p>对应 YAML 配置示例：
     * <pre>{@code
     * structure:
     *   infra:
     *     stream:
     *       router:
     *         routes:
     *           - id: order-create
     *             event-type: order
     *             business-type: create
     *             payload-type: com.example.OrderPayload
     *             condition: "#payload.amount > 100"
     *             handler-bean: orderHandler
     *             handler-method: handleCreate
     * }</pre>
     */
    public static class RouteDefinition {
        /**
         * 路由唯一标识，用于日志与去重。
         */
        private String id;
        /**
         * 事件类型，路由匹配第 1 步键。
         */
        private String eventType;
        /**
         * 业务类型，路由匹配第 2 步筛选条件，支持 {@code "*"} 通配符。
         */
        private String businessType;
        /**
         * 负载类型全限定类名，路由匹配第 3 步类型检查依据。
         */
        private String payloadType;
        /**
         * SpEL 条件表达式，路由匹配第 4 步求值依据。
         */
        private String condition;
        /**
         * 处理器 Bean 名称，由 {@link ConfigurableRouteInitializer} 通过 ApplicationContext 查找。
         */
        private String handlerBean;
        /**
         * 处理器方法名，由 {@link ConfigurableRouteInitializer} 反射调用。
         */
        private String handlerMethod;
        /**
         * 路由描述，仅用于文档与日志，不参与匹配。
         */
        private String description;

        /**
         * @return 路由唯一标识
         */
        public String getId() {
            return id;
        }

        /**
         * @param id 路由唯一标识
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * @return 事件类型
         */
        public String getEventType() {
            return eventType;
        }

        /**
         * @param eventType 事件类型
         */
        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        /**
         * @return 业务类型
         */
        public String getBusinessType() {
            return businessType;
        }

        /**
         * @param businessType 业务类型
         */
        public void setBusinessType(String businessType) {
            this.businessType = businessType;
        }

        /**
         * @return 负载类型全限定类名
         */
        public String getPayloadType() {
            return payloadType;
        }

        /**
         * @param payloadType 负载类型全限定类名
         */
        public void setPayloadType(String payloadType) {
            this.payloadType = payloadType;
        }

        /**
         * @return SpEL 条件表达式
         */
        public String getCondition() {
            return condition;
        }

        /**
         * @param condition SpEL 条件表达式
         */
        public void setCondition(String condition) {
            this.condition = condition;
        }

        /**
         * @return 处理器 Bean 名称
         */
        public String getHandlerBean() {
            return handlerBean;
        }

        /**
         * @param handlerBean 处理器 Bean 名称
         */
        public void setHandlerBean(String handlerBean) {
            this.handlerBean = handlerBean;
        }

        /**
         * @return 处理器方法名
         */
        public String getHandlerMethod() {
            return handlerMethod;
        }

        /**
         * @param handlerMethod 处理器方法名
         */
        public void setHandlerMethod(String handlerMethod) {
            this.handlerMethod = handlerMethod;
        }

        /**
         * @return 路由描述
         */
        public String getDescription() {
            return description;
        }

        /**
         * @param description 路由描述
         */
        public void setDescription(String description) {
            this.description = description;
        }
    }
}
