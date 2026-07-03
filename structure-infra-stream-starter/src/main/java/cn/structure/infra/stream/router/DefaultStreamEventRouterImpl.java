package cn.structure.infra.stream.router;

import cn.structure.infra.stream.event.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultStreamEventRouterImpl implements StreamEventRouter {

    private static final Logger log = LoggerFactory.getLogger(DefaultStreamEventRouterImpl.class);

    private final Map<String, List<RouteRegistration<?>>> routeRegistrations = new ConcurrentHashMap<>();
    private final SpelExpressionParser expressionParser = new SpelExpressionParser();
    private final java.util.concurrent.atomic.AtomicLong handlerCounter = new java.util.concurrent.atomic.AtomicLong(0);

    @Override
    public <T> void registerRoute(String eventType, Class<T> payloadType, StreamRouteHandler<T> handler) {
        registerRoute(eventType, "", payloadType, "", handler);
    }

    @Override
    public <T> void registerRoute(String eventType, Class<T> payloadType, String condition, StreamRouteHandler<T> handler) {
        registerRoute(eventType, "", payloadType, condition, handler);
    }

    @Override
    public <T> void registerRoute(String eventType, String businessType, Class<T> payloadType, StreamRouteHandler<T> handler) {
        registerRoute(eventType, businessType, payloadType, "", handler);
    }

    @Override
    public <T> void registerRoute(String eventType, String businessType, Class<T> payloadType,
                                  String condition, StreamRouteHandler<T> handler) {
        String handlerId = generateHandlerId(eventType, businessType, payloadType);
        RouteRegistration<T> registration = new RouteRegistration<>(handlerId, eventType, businessType,
                payloadType, condition, handler);

        routeRegistrations.computeIfAbsent(eventType, k -> new ArrayList<>()).add(registration);

        log.info("Registered route: eventType={}, businessType={}, payloadType={}, condition={}",
                eventType, businessType, payloadType.getName(), condition);
    }

    @Override
    public void unregisterRoute(String eventType) {
        routeRegistrations.remove(eventType);
        log.info("Unregistered all routes for eventType: {}", eventType);
    }

    @Override
    public void unregisterRoute(String eventType, String handlerId) {
        List<RouteRegistration<?>> registrations = routeRegistrations.get(eventType);
        if (registrations != null) {
            boolean removed = registrations.removeIf(r -> r.getHandlerId().equals(handlerId));
            if (removed) {
                log.info("Unregistered route: {} for eventType: {}", handlerId, eventType);
            }
            if (registrations.isEmpty()) {
                routeRegistrations.remove(eventType);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void route(StreamEvent<T> event) {
        if (event == null || event.getEventType() == null) {
            log.warn("Cannot route null event or event with null eventType");
            return;
        }

        String eventType = event.getEventType();
        List<RouteRegistration<?>> registrations = routeRegistrations.get(eventType);

        if (registrations == null || registrations.isEmpty()) {
            log.debug("No routes registered for eventType: {}", eventType);
            return;
        }

        log.debug("Routing event: eventId={}, eventType={}, businessType={}",
                event.getEventId(), eventType, event.getBusinessType());

        for (RouteRegistration<?> registration : registrations) {
            if (matchesBusinessType(registration.getBusinessType(), event.getBusinessType()) &&
                    registration.getPayloadType().isInstance(event.getPayload()) &&
                    matchesCondition(registration.getCondition(), event.getPayload())) {
                try {
                    ((StreamRouteHandler<T>) registration.getHandler()).handle(event.getPayload(), event);
                    log.debug("Dispatched event to handler: {} for eventType: {}", registration.getHandlerId(), eventType);
                } catch (Exception e) {
                    log.error("Error handling event in handler: {} for eventType: {}", registration.getHandlerId(), eventType, e);
                }
            }
        }
    }

    @Override
    public boolean isRouteRegistered(String eventType) {
        return routeRegistrations.containsKey(eventType) && !routeRegistrations.get(eventType).isEmpty();
    }

    @Override
    public List<RouteRegistration<?>> getRoutes(String eventType) {
        return routeRegistrations.getOrDefault(eventType, new ArrayList<>());
    }

    private String generateHandlerId(String eventType, String businessType, Class<?> payloadType) {
        return eventType + ":" + (businessType != null ? businessType : "default") + ":" + payloadType.getSimpleName() + ":" + handlerCounter.incrementAndGet();
    }

    private boolean matchesBusinessType(String pattern, String businessType) {
        if (pattern == null || pattern.isEmpty() || "*".equals(pattern)) {
            return true;
        }
        return pattern.equals(businessType);
    }

    private <T> boolean matchesCondition(String condition, T payload) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }

        try {
            Expression expression = expressionParser.parseExpression(condition);
            EvaluationContext context = new StandardEvaluationContext();
            context.setVariable("payload", payload);
            Boolean result = expression.getValue(context, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Failed to evaluate condition: {} for payload: {}", condition, payload, e);
            return false;
        }
    }
}
