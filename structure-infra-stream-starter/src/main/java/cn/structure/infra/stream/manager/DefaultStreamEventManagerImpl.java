package cn.structure.infra.stream.manager;

import cn.structure.infra.stream.handler.StreamEventHandler;
import cn.structure.infra.stream.properties.StreamProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultStreamEventManagerImpl implements StreamEventManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultStreamEventManagerImpl.class);

    private final StreamBridge streamBridge;
    private final StreamProperties streamProperties;
    private final Map<String, List<ListenerRegistration<?>>> registeredListeners = new ConcurrentHashMap<>();
    private final SpelExpressionParser expressionParser = new SpelExpressionParser();

    public DefaultStreamEventManagerImpl(StreamBridge streamBridge, StreamProperties streamProperties) {
        this.streamBridge = streamBridge;
        this.streamProperties = streamProperties;
    }

    @Override
    public <T> void publish(String bindingName, T event) {
        StreamProperties.Binding binding = streamProperties.getBindings().get(bindingName);
        if (binding == null) {
            throw new IllegalArgumentException("Binding not found: " + bindingName);
        }
        publish(bindingName, binding.getDestination(), binding.getGroup(), event);
    }

    @Override
    public <T> void publish(String bindingName, String destination, T event) {
        String group = streamProperties.getDefaultGroup();
        publish(bindingName, destination, group, event);
    }

    @Override
    public <T> void publish(String bindingName, String destination, String group, T event) {
        ensureBindingRegistered(bindingName, destination, group);

        String outputBindingName = bindingName + "-out-0";
        Message<T> message = MessageBuilder.withPayload(event).build();
        streamBridge.send(outputBindingName, message);
        log.debug("Published event to binding: {}, destination: {}, group: {}", outputBindingName, destination, group);
    }

    private synchronized void ensureBindingRegistered(String bindingName, String destination, String group) {
        if (!streamProperties.getBindings().containsKey(bindingName)) {
            registerBinding(bindingName, destination, group);
        }
    }

    @Override
    public <T> void registerListener(String bindingName, Class<T> eventType, StreamEventHandler<T> handler) {
        registerListener(bindingName, eventType, "", handler);
    }

    @Override
    public <T> void registerListener(String bindingName, Class<T> eventType, String condition, StreamEventHandler<T> handler) {
        StreamProperties.Binding binding = streamProperties.getBindings().get(bindingName);
        if (binding == null) {
            throw new IllegalArgumentException("Binding not found: " + bindingName);
        }
        registerListener(bindingName, binding.getDestination(), binding.getGroup(), eventType, condition, handler);
    }

    @Override
    public <T> void registerListener(String bindingName, String destination, String group, Class<T> eventType, StreamEventHandler<T> handler) {
        registerListener(bindingName, destination, group, eventType, "", handler);
    }

    @Override
    public <T> void registerListener(String bindingName, String destination, String group, Class<T> eventType, String condition, StreamEventHandler<T> handler) {
        String listenerId = UUID.randomUUID().toString();
        ListenerRegistration<T> registration = ListenerRegistration.<T>builder()
                .listenerId(listenerId)
                .eventType(eventType)
                .handler(handler)
                .condition(condition)
                .destination(destination)
                .group(group)
                .build();

        registeredListeners.computeIfAbsent(bindingName, k -> new ArrayList<>()).add(registration);

        log.info("Registered listener for binding: {}, listenerId: {}, destination: {}, group: {}, eventType: {}, condition: {}",
                bindingName, listenerId, destination, group, eventType.getName(), condition);
    }

    @Override
    public void unregisterListener(String bindingName) {
        registeredListeners.remove(bindingName);
        log.info("Unregistered all listeners for binding: {}", bindingName);
    }

    @Override
    public void unregisterListener(String bindingName, String listenerId) {
        List<ListenerRegistration<?>> registrations = registeredListeners.get(bindingName);
        if (registrations != null) {
            boolean removed = registrations.removeIf(r -> r.getListenerId().equals(listenerId));
            if (removed) {
                log.info("Unregistered listener: {} for binding: {}", listenerId, bindingName);
            }
            if (registrations.isEmpty()) {
                registeredListeners.remove(bindingName);
            }
        }
    }

    @Override
    public boolean isListenerRegistered(String bindingName) {
        return registeredListeners.containsKey(bindingName) && !registeredListeners.get(bindingName).isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void dispatch(String bindingName, T event) {
        List<ListenerRegistration<?>> registrations = registeredListeners.get(bindingName);
        if (registrations == null || registrations.isEmpty()) {
            log.debug("No listeners registered for binding: {}", bindingName);
            return;
        }

        for (ListenerRegistration<?> registration : registrations) {
            if (!registration.getEventType().isInstance(event)) {
                continue;
            }

            if (matchesCondition(registration.getCondition(), event)) {
                try {
                    ((StreamEventHandler<T>) registration.getHandler()).handle(event);
                    log.debug("Dispatched event to listener: {} for binding: {}", registration.getListenerId(), bindingName);
                } catch (Exception e) {
                    log.error("Error handling event in listener: {} for binding: {}", registration.getListenerId(), bindingName, e);
                }
            }
        }
    }

    @Override
    public List<ListenerRegistration<?>> getListeners(String bindingName) {
        return registeredListeners.getOrDefault(bindingName, new ArrayList<>());
    }

    @Override
    public void registerBinding(String bindingName, String destination) {
        registerBinding(bindingName, destination, streamProperties.getDefaultGroup());
    }

    @Override
    public void registerBinding(String bindingName, String destination, String group) {
        registerBinding(bindingName, destination, group, streamProperties.getDefaultContentType(), streamProperties.getDefaultConcurrency());
    }

    @Override
    public void registerBinding(String bindingName, String destination, String group, String contentType, Integer concurrency) {
        synchronized (this) {
            if (streamProperties.getBindings().containsKey(bindingName)) {
                log.warn("Binding already registered: {}", bindingName);
                return;
            }

            StreamProperties.Binding binding = new StreamProperties.Binding();
            binding.setDestination(destination);
            binding.setGroup(group);
            binding.setContentType(contentType != null ? contentType : streamProperties.getDefaultContentType());
            binding.setConcurrency(concurrency != null ? concurrency : streamProperties.getDefaultConcurrency());

            streamProperties.getBindings().put(bindingName, binding);

            log.info("Dynamically registered binding: {}, destination: {}, group: {}, contentType: {}",
                    bindingName, destination, group, binding.getContentType());
        }
    }

    @Override
    public void unregisterBinding(String bindingName) {
        synchronized (this) {
            StreamProperties.Binding removed = streamProperties.getBindings().remove(bindingName);
            if (removed != null) {
                unregisterListener(bindingName);
                log.info("Dynamically unregistered binding: {}", bindingName);
            }
        }
    }

    @Override
    public boolean isBindingRegistered(String bindingName) {
        return streamProperties.getBindings().containsKey(bindingName);
    }

    @Override
    public StreamProperties.Binding getBinding(String bindingName) {
        return streamProperties.getBindings().get(bindingName);
    }

    @Override
    public Map<String, StreamProperties.Binding> getAllBindings() {
        return streamProperties.getBindings();
    }

    private <T> boolean matchesCondition(String condition, T event) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }

        try {
            Expression expression = expressionParser.parseExpression(condition);
            EvaluationContext context = new StandardEvaluationContext();
            context.setVariable("event", event);
            Boolean result = expression.getValue(context, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Failed to evaluate condition: {} for event: {}", condition, event, e);
            return false;
        }
    }
}
