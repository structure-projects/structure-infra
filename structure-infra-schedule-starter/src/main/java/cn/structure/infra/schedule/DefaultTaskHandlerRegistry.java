package cn.structure.infra.schedule;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class DefaultTaskHandlerRegistry implements TaskHandlerRegistry {

    private final Map<String, TaskHandler> handlerMap = new ConcurrentHashMap<>();

    @Override
    public void register(String handlerName, TaskHandler handler) {
        if (handlerName == null || handler == null) {
            throw new IllegalArgumentException("Handler name and handler cannot be null");
        }
        handlerMap.put(handlerName, handler);
        log.info("Registered task handler: {}", handlerName);
    }

    @Override
    public TaskHandler get(String handlerName) {
        return handlerMap.get(handlerName);
    }

    @Override
    public void unregister(String handlerName) {
        handlerMap.remove(handlerName);
        log.info("Unregistered task handler: {}", handlerName);
    }

    @Override
    public boolean contains(String handlerName) {
        return handlerMap.containsKey(handlerName);
    }
}