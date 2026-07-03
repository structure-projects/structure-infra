package cn.structure.infra.stream.manager;

import cn.structure.infra.stream.handler.StreamEventHandler;

public class ListenerRegistration<T> {

    private String listenerId;
    private Class<T> eventType;
    private StreamEventHandler<T> handler;
    private String condition;
    private String destination;
    private String group;

    public ListenerRegistration() {
    }

    public ListenerRegistration(String listenerId, Class<T> eventType, StreamEventHandler<T> handler,
                                String condition, String destination, String group) {
        this.listenerId = listenerId;
        this.eventType = eventType;
        this.handler = handler;
        this.condition = condition;
        this.destination = destination;
        this.group = group;
    }

    public String getListenerId() {
        return listenerId;
    }

    public void setListenerId(String listenerId) {
        this.listenerId = listenerId;
    }

    public Class<T> getEventType() {
        return eventType;
    }

    public void setEventType(Class<T> eventType) {
        this.eventType = eventType;
    }

    public StreamEventHandler<T> getHandler() {
        return handler;
    }

    public void setHandler(StreamEventHandler<T> handler) {
        this.handler = handler;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private String listenerId;
        private Class<T> eventType;
        private StreamEventHandler<T> handler;
        private String condition;
        private String destination;
        private String group;

        public Builder<T> listenerId(String listenerId) {
            this.listenerId = listenerId;
            return this;
        }

        public Builder<T> eventType(Class<T> eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder<T> handler(StreamEventHandler<T> handler) {
            this.handler = handler;
            return this;
        }

        public Builder<T> condition(String condition) {
            this.condition = condition;
            return this;
        }

        public Builder<T> destination(String destination) {
            this.destination = destination;
            return this;
        }

        public Builder<T> group(String group) {
            this.group = group;
            return this;
        }

        public ListenerRegistration<T> build() {
            return new ListenerRegistration<>(listenerId, eventType, handler, condition, destination, group);
        }
    }
}
