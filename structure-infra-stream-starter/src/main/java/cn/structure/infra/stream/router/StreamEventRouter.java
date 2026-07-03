package cn.structure.infra.stream.router;

import cn.structure.infra.stream.event.StreamEvent;

import java.util.List;

public interface StreamEventRouter {

    <T> void registerRoute(String eventType, Class<T> payloadType, StreamRouteHandler<T> handler);

    <T> void registerRoute(String eventType, Class<T> payloadType, String condition, StreamRouteHandler<T> handler);

    <T> void registerRoute(String eventType, String businessType, Class<T> payloadType, StreamRouteHandler<T> handler);

    <T> void registerRoute(String eventType, String businessType, Class<T> payloadType, String condition, StreamRouteHandler<T> handler);

    void unregisterRoute(String eventType);

    void unregisterRoute(String eventType, String handlerId);

    <T> void route(StreamEvent<T> event);

    boolean isRouteRegistered(String eventType);

    List<RouteRegistration<?>> getRoutes(String eventType);

    interface StreamRouteHandler<T> {
        void handle(T payload, StreamEvent<T> event);
    }

}
