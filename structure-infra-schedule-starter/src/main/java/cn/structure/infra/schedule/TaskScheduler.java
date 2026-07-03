package cn.structure.infra.schedule;

import java.util.List;

public interface TaskScheduler {

    void schedule(ScheduleTask task);

    void update(ScheduleTask task);

    void remove(String taskId);

    void pause(String taskId);

    void resume(String taskId);

    ScheduleTask getTaskInfo(String taskId);

    List<ScheduleTask> getAllTasks();
}