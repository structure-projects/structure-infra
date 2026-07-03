package cn.structure.infra.stream.handler;

public interface StreamEventHandler<T> {

    void handle(T event);

}
