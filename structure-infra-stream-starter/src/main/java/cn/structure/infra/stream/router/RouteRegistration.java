package cn.structure.infra.stream.router;

public class RouteRegistration<T> {

    private String handlerId;
    private String eventType;
    private String businessType;
    private Class<T> payloadType;
    private String condition;
    private StreamEventRouter.StreamRouteHandler<T> handler;

    public RouteRegistration() {
    }

    public RouteRegistration(String handlerId, String eventType, String businessType,
                            Class<T> payloadType, String condition, StreamEventRouter.StreamRouteHandler<T> handler) {
        this.handlerId = handlerId;
        this.eventType = eventType;
        this.businessType = businessType;
        this.payloadType = payloadType;
        this.condition = condition;
        this.handler = handler;
    }

    public String getHandlerId() {
        return handlerId;
    }

    public void setHandlerId(String handlerId) {
        this.handlerId = handlerId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public Class<T> getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(Class<T> payloadType) {
        this.payloadType = payloadType;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public StreamEventRouter.StreamRouteHandler<T> getHandler() {
        return handler;
    }

    public void setHandler(StreamEventRouter.StreamRouteHandler<T> handler) {
        this.handler = handler;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private String handlerId;
        private String eventType;
        private String businessType;
        private Class<T> payloadType;
        private String condition;
        private StreamEventRouter.StreamRouteHandler<T> handler;

        public Builder<T> handlerId(String handlerId) {
            this.handlerId = handlerId;
            return this;
        }

        public Builder<T> eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder<T> businessType(String businessType) {
            this.businessType = businessType;
            return this;
        }

        public Builder<T> payloadType(Class<T> payloadType) {
            this.payloadType = payloadType;
            return this;
        }

        public Builder<T> condition(String condition) {
            this.condition = condition;
            return this;
        }

        public Builder<T> handler(StreamEventRouter.StreamRouteHandler<T> handler) {
            this.handler = handler;
            return this;
        }

        public RouteRegistration<T> build() {
            return new RouteRegistration<>(handlerId, eventType, businessType, payloadType, condition, handler);
        }
    }
}
