package cn.structure.infra.stream.manager;

import cn.structure.infra.stream.handler.StreamEventHandler;
import cn.structure.infra.stream.properties.StreamProperties;

import java.util.List;
import java.util.Map;

public interface StreamEventManager {

    <T> void publish(String bindingName, T event);

    <T> void publish(String bindingName, String destination, T event);

    <T> void publish(String bindingName, String destination, String group, T event);

    <T> void registerListener(String bindingName, Class<T> eventType, StreamEventHandler<T> handler);

    <T> void registerListener(String bindingName, Class<T> eventType, String condition, StreamEventHandler<T> handler);

    <T> void registerListener(String bindingName, String destination, String group, Class<T> eventType, StreamEventHandler<T> handler);

    <T> void registerListener(String bindingName, String destination, String group, Class<T> eventType, String condition, StreamEventHandler<T> handler);

    void unregisterListener(String bindingName);

    void unregisterListener(String bindingName, String listenerId);

    boolean isListenerRegistered(String bindingName);

    <T> void dispatch(String bindingName, T event);

    List<ListenerRegistration<?>> getListeners(String bindingName);

    void registerBinding(String bindingName, String destination);

    void registerBinding(String bindingName, String destination, String group);

    void registerBinding(String bindingName, String destination, String group, String contentType, Integer concurrency);

    void unregisterBinding(String bindingName);

    boolean isBindingRegistered(String bindingName);

    StreamProperties.Binding getBinding(String bindingName);

    Map<String, StreamProperties.Binding> getAllBindings();

}
