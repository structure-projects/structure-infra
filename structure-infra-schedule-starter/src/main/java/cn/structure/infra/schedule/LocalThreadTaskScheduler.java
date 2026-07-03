package cn.structure.infra.schedule;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class LocalThreadTaskScheduler implements TaskScheduler {

    private final ScheduledExecutorService executorService;

    private final Map<String, ScheduledFuture<?>> futureMap = new ConcurrentHashMap<>();

    private final Map<String, ScheduleTask> taskMap = new ConcurrentHashMap<>();

    private final TaskHandlerRegistry handlerRegistry;

    public LocalThreadTaskScheduler(TaskHandlerRegistry handlerRegistry) {
        this(Runtime.getRuntime().availableProcessors(), handlerRegistry);
    }

    public LocalThreadTaskScheduler(int poolSize, TaskHandlerRegistry handlerRegistry) {
        this.executorService = Executors.newScheduledThreadPool(poolSize, r -> {
            Thread thread = new Thread(r);
            thread.setName("structure-schedule-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
        this.handlerRegistry = handlerRegistry;
        log.info("LocalThreadTaskScheduler initialized with pool size: {}", poolSize);
    }

    @Override
    public void schedule(ScheduleTask task) {
        validateTask(task);

        remove(task.getTaskId());

        ScheduledFuture<?> future;
        Runnable wrappedRunnable = wrapRunnable(task);

        switch (task.getScheduleType()) {
            case FIXED_DELAY:
                long delay = task.getDelay() != null ? task.getDelay() : 1000L;
                long initialDelay = task.getInitialDelay() != null ? task.getInitialDelay() : 0L;
                TimeUnit timeUnit = task.getTimeUnit() != null ? task.getTimeUnit() : TimeUnit.MILLISECONDS;
                future = executorService.scheduleWithFixedDelay(wrappedRunnable, initialDelay, delay, timeUnit);
                break;

            case FIXED_RATE:
                long period = task.getPeriod() != null ? task.getPeriod() : 1000L;
                initialDelay = task.getInitialDelay() != null ? task.getInitialDelay() : 0L;
                timeUnit = task.getTimeUnit() != null ? task.getTimeUnit() : TimeUnit.MILLISECONDS;
                future = executorService.scheduleAtFixedRate(wrappedRunnable, initialDelay, period, timeUnit);
                break;

            case CRON:
                if (task.getCronExpression() == null || task.getCronExpression().isEmpty()) {
                    throw new IllegalArgumentException("Cron expression cannot be null for CRON schedule type");
                }
                future = scheduleCronTask(task, wrappedRunnable);
                break;

            default:
                throw new IllegalArgumentException("Unsupported schedule type: " + task.getScheduleType());
        }

        futureMap.put(task.getTaskId(), future);
        task.setStatus(ScheduleTask.TaskStatus.RUNNING);
        taskMap.put(task.getTaskId(), task);

        log.info("Scheduled task: id={}, name={}, type={}, handler={}", task.getTaskId(), task.getTaskName(), task.getScheduleType(), task.getHandlerName());
    }

    @Override
    public void update(ScheduleTask task) {
        validateTask(task);

        ScheduleTask existingTask = taskMap.get(task.getTaskId());
        if (existingTask == null) {
            log.warn("Task not found for update: {}", task.getTaskId());
            return;
        }

        schedule(task);
        log.info("Updated task: id={}", task.getTaskId());
    }

    private void validateTask(ScheduleTask task) {
        if (task == null || task.getTaskId() == null) {
            throw new IllegalArgumentException("Task and taskId cannot be null");
        }

        if (task.getHandlerName() == null || task.getHandlerName().isEmpty()) {
            throw new IllegalArgumentException("Handler name cannot be null or empty");
        }

        if (!handlerRegistry.contains(task.getHandlerName())) {
            throw new IllegalArgumentException("Handler not found: " + task.getHandlerName());
        }

        if (task.getScheduleType() == null) {
            throw new IllegalArgumentException("ScheduleType cannot be null");
        }
    }

    private ScheduledFuture<?> scheduleCronTask(ScheduleTask task, Runnable wrappedRunnable) {
        return executorService.scheduleAtFixedRate(() -> {
            try {
                wrappedRunnable.run();
            } catch (Exception e) {
                log.error("Cron task execution failed: id={}, error={}", task.getTaskId(), e.getMessage(), e);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private Runnable wrapRunnable(ScheduleTask task) {
        return () -> {
            try {
                TaskHandler handler = handlerRegistry.get(task.getHandlerName());
                if (handler != null) {
                    handler.execute(task.getHandlerParam());
                } else {
                    log.error("Handler not found during execution: {}", task.getHandlerName());
                }
            } catch (Exception e) {
                log.error("Task execution failed: id={}, name={}, handler={}, error={}", task.getTaskId(), task.getTaskName(), task.getHandlerName(), e.getMessage(), e);
            }
        };
    }

    @Override
    public void remove(String taskId) {
        ScheduledFuture<?> future = futureMap.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }

        ScheduleTask task = taskMap.remove(taskId);
        if (task != null) {
            task.setStatus(ScheduleTask.TaskStatus.STOPPED);
        }

        log.info("Removed task: id={}", taskId);
    }

    @Override
    public void pause(String taskId) {
        ScheduledFuture<?> future = futureMap.get(taskId);
        if (future != null) {
            future.cancel(false);
            ScheduleTask task = taskMap.get(taskId);
            if (task != null) {
                task.setStatus(ScheduleTask.TaskStatus.PAUSED);
            }
            log.info("Paused task: id={}", taskId);
        }
    }

    @Override
    public void resume(String taskId) {
        ScheduleTask task = taskMap.get(taskId);
        if (task != null && task.getStatus() == ScheduleTask.TaskStatus.PAUSED) {
            schedule(task);
            log.info("Resumed task: id={}", taskId);
        }
    }

    @Override
    public ScheduleTask getTaskInfo(String taskId) {
        return taskMap.get(taskId);
    }

    @Override
    public List<ScheduleTask> getAllTasks() {
        return List.copyOf(taskMap.values());
    }
}