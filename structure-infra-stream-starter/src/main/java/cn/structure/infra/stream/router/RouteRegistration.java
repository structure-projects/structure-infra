package cn.structure.infra.stream.router;

/**
 * 路由注册信息，承载单个路由处理器在 {@link StreamEventRouter} 中的全部上下文。
 *
 * <p>设计意图：
 * <ul>
 *   <li>将路由处理器与其 4 个匹配维度（eventType/businessType/payloadType/condition）打包为一等公民对象，
 *       便于 {@link DefaultStreamEventRouterImpl#route} 时统一迭代过滤</li>
 *   <li>handlerId 由 {@code DefaultStreamEventRouterImpl} 生成，作为精确注销的句柄</li>
 *   <li>支持 Builder 模式构建</li>
 * </ul>
 *
 * <p>协作关系：
 * <ul>
 *   <li>由 {@link DefaultStreamEventRouterImpl#registerRoute} 创建并加入注册表</li>
 *   <li>由 {@link DefaultStreamEventRouterImpl#route} 在路由时读取 businessType/payloadType/condition 进行过滤</li>
 * </ul>
 *
 * @param <T> 负载类型
 */
public class RouteRegistration<T> {

    /**
     * 路由唯一 ID，注册时生成，用于精确注销。
     */
    private String handlerId;
    /**
     * 事件类型，路由匹配第 1 步精确匹配键（作为 Map key）。
     */
    private String eventType;
    /**
     * 业务类型，路由匹配第 2 步筛选条件，支持 {@code "*"} 通配符。
     */
    private String businessType;
    /**
     * 负载类型，路由匹配第 3 步类型检查依据（isInstance 判断）。
     */
    private Class<T> payloadType;
    /**
     * SpEL 条件表达式，路由匹配第 4 步求值依据，可通过 {@code #payload} 引用负载。
     */
    private String condition;
    /**
     * 路由处理器回调。
     */
    private StreamEventRouter.StreamRouteHandler<T> handler;

    /**
     * 默认构造方法，供反序列化或 Builder 使用。
     */
    public RouteRegistration() {
    }

    /**
     * 全参构造方法。
     *
     * @param handlerId    路由唯一 ID
     * @param eventType    事件类型
     * @param businessType 业务类型
     * @param payloadType  负载类型
     * @param condition    SpEL 条件表达式
     * @param handler      路由处理器
     */
    public RouteRegistration(String handlerId, String eventType, String businessType,
                            Class<T> payloadType, String condition, StreamEventRouter.StreamRouteHandler<T> handler) {
        this.handlerId = handlerId;
        this.eventType = eventType;
        this.businessType = businessType;
        this.payloadType = payloadType;
        this.condition = condition;
        this.handler = handler;
    }

    /**
     * @return 路由唯一 ID
     */
    public String getHandlerId() {
        return handlerId;
    }

    /**
     * @param handlerId 路由唯一 ID
     */
    public void setHandlerId(String handlerId) {
        this.handlerId = handlerId;
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
     * @return 负载类型
     */
    public Class<T> getPayloadType() {
        return payloadType;
    }

    /**
     * @param payloadType 负载类型
     */
    public void setPayloadType(Class<T> payloadType) {
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
     * @return 路由处理器
     */
    public StreamEventRouter.StreamRouteHandler<T> getHandler() {
        return handler;
    }

    /**
     * @param handler 路由处理器
     */
    public void setHandler(StreamEventRouter.StreamRouteHandler<T> handler) {
        this.handler = handler;
    }

    /**
     * 创建一个 Builder 以便链式构建注册信息。
     *
     * @param <T> 负载类型
     * @return 新的 Builder 实例
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * RouteRegistration 的链式构建器。
     *
     * @param <T> 负载类型
     */
    public static class Builder<T> {
        private String handlerId;
        private String eventType;
        private String businessType;
        private Class<T> payloadType;
        private String condition;
        private StreamEventRouter.StreamRouteHandler<T> handler;

        /**
         * @param handlerId 路由唯一 ID
         * @return 当前 Builder
         */
        public Builder<T> handlerId(String handlerId) {
            this.handlerId = handlerId;
            return this;
        }

        /**
         * @param eventType 事件类型
         * @return 当前 Builder
         */
        public Builder<T> eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        /**
         * @param businessType 业务类型
         * @return 当前 Builder
         */
        public Builder<T> businessType(String businessType) {
            this.businessType = businessType;
            return this;
        }

        /**
         * @param payloadType 负载类型
         * @return 当前 Builder
         */
        public Builder<T> payloadType(Class<T> payloadType) {
            this.payloadType = payloadType;
            return this;
        }

        /**
         * @param condition SpEL 条件表达式
         * @return 当前 Builder
         */
        public Builder<T> condition(String condition) {
            this.condition = condition;
            return this;
        }

        /**
         * @param handler 路由处理器
         * @return 当前 Builder
         */
        public Builder<T> handler(StreamEventRouter.StreamRouteHandler<T> handler) {
            this.handler = handler;
            return this;
        }

        /**
         * 终结方法，生成 {@link RouteRegistration} 实例。
         *
         * @return 新构建的注册信息
         */
        public RouteRegistration<T> build() {
            return new RouteRegistration<>(handlerId, eventType, businessType, payloadType, condition, handler);
        }
    }
}
