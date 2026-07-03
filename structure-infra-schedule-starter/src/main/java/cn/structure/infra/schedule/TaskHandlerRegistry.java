package cn.structure.infra.schedule;

public interface TaskHandlerRegistry {

    void register(String handlerName, TaskHandler handler);

    TaskHandler get(String handlerName);

    void unregister(String handlerName);

    boolean contains(String handlerName);
}