package cn.structure.infra.schedule.xxljob;

import cn.structure.infra.schedule.ScheduleTask;
import cn.structure.infra.schedule.TaskScheduler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class XxlJobTaskScheduler implements TaskScheduler {

    private final XxlJobTemplate xxlJobTemplate;

    private final Map<String, String> taskIdToXxlJobIdMap = new ConcurrentHashMap<>();

    private final Map<String, ScheduleTask> taskMap = new ConcurrentHashMap<>();

    public XxlJobTaskScheduler(XxlJobTemplate xxlJobTemplate) {
        this.xxlJobTemplate = xxlJobTemplate;
        log.info("XxlJobTaskScheduler initialized");
    }

    @Override
    public void schedule(ScheduleTask task) {
        validateTask(task);

        remove(task.getTaskId());

        String cronExpression = convertToCron(task);

        String xxlJobId = xxlJobTemplate.add(
                task.getTaskName(),
                cronExpression,
                task.getHandlerName(),
                task.getHandlerParam()
        );

        taskIdToXxlJobIdMap.put(task.getTaskId(), xxlJobId);
        task.setStatus(ScheduleTask.TaskStatus.RUNNING);
        taskMap.put(task.getTaskId(), task);

        log.info("Scheduled task via XXL-Job: taskId={}, xxlJobId={}, handler={}",
                task.getTaskId(), xxlJobId, task.getHandlerName());
    }

    @Override
    public void update(ScheduleTask task) {
        validateTask(task);

        String xxlJobId = taskIdToXxlJobIdMap.get(task.getTaskId());
        if (xxlJobId == null) {
            log.warn("XXL-Job task not found for update: {}", task.getTaskId());
            schedule(task);
            return;
        }

        String cronExpression = convertToCron(task);

        xxlJobTemplate.update(
                xxlJobId,
                task.getTaskName(),
                cronExpression,
                task.getHandlerName(),
                task.getHandlerParam()
        );

        task.setStatus(ScheduleTask.TaskStatus.RUNNING);
        taskMap.put(task.getTaskId(), task);

        log.info("Updated task via XXL-Job: taskId={}, xxlJobId={}", task.getTaskId(), xxlJobId);
    }

    @Override
    public void remove(String taskId) {
        String xxlJobId = taskIdToXxlJobIdMap.remove(taskId);
        if (xxlJobId != null) {
            xxlJobTemplate.remove(xxlJobId);
        }

        ScheduleTask task = taskMap.remove(taskId);
        if (task != null) {
            task.setStatus(ScheduleTask.TaskStatus.STOPPED);
        }

        log.info("Removed task via XXL-Job: taskId={}, xxlJobId={}", taskId, xxlJobId);
    }

    @Override
    public void pause(String taskId) {
        String xxlJobId = taskIdToXxlJobIdMap.get(taskId);
        if (xxlJobId != null) {
            xxlJobTemplate.pause(xxlJobId);

            ScheduleTask task = taskMap.get(taskId);
            if (task != null) {
                task.setStatus(ScheduleTask.TaskStatus.PAUSED);
            }
        }

        log.info("Paused task via XXL-Job: taskId={}, xxlJobId={}", taskId, xxlJobId);
    }

    @Override
    public void resume(String taskId) {
        String xxlJobId = taskIdToXxlJobIdMap.get(taskId);
        if (xxlJobId != null) {
            ScheduleTask task = taskMap.get(taskId);
            if (task != null && task.getStatus() == ScheduleTask.TaskStatus.PAUSED) {
                xxlJobTemplate.start(xxlJobId);
                task.setStatus(ScheduleTask.TaskStatus.RUNNING);
            }
        }

        log.info("Resumed task via XXL-Job: taskId={}, xxlJobId={}", taskId, xxlJobId);
    }

    @Override
    public ScheduleTask getTaskInfo(String taskId) {
        return taskMap.get(taskId);
    }

    @Override
    public List<ScheduleTask> getAllTasks() {
        return List.copyOf(taskMap.values());
    }

    private void validateTask(ScheduleTask task) {
        if (task == null || task.getTaskId() == null) {
            throw new IllegalArgumentException("Task and taskId cannot be null");
        }

        if (task.getHandlerName() == null || task.getHandlerName().isEmpty()) {
            throw new IllegalArgumentException("Handler name cannot be null or empty");
        }

        if (task.getScheduleType() == null) {
            throw new IllegalArgumentException("ScheduleType cannot be null");
        }
    }

    private String convertToCron(ScheduleTask task) {
        if (task.getScheduleType() == ScheduleTask.ScheduleType.CRON) {
            if (task.getCronExpression() == null || task.getCronExpression().isEmpty()) {
                throw new IllegalArgumentException("Cron expression cannot be null for CRON schedule type");
            }
            return task.getCronExpression();
        }

        long milliseconds = task.getScheduleType() == ScheduleTask.ScheduleType.FIXED_RATE
                ? (task.getPeriod() != null ? task.getPeriod() : 1000L)
                : (task.getDelay() != null ? task.getDelay() : 1000L);

        long seconds = milliseconds / 1000;

        if (seconds < 1) {
            seconds = 1;
        }

        return "0/" + seconds + " * * * * ?";
    }
}