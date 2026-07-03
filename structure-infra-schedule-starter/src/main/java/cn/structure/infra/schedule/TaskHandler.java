package cn.structure.infra.schedule;

@FunctionalInterface
public interface TaskHandler {

    void execute(String param);
}