package cn.structure.infra.schedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SpringTaskSchedulerAdapter implements TaskScheduler {

    private final LocalThreadTaskScheduler localThreadTaskScheduler;

    private final TaskHandlerRegistry handlerRegistry;

    public SpringTaskSchedulerAdapter(LocalThreadTaskScheduler localThreadTaskScheduler,
                                     TaskHandlerRegistry handlerRegistry) {
        this.localThreadTaskScheduler = localThreadTaskScheduler;
        this.handlerRegistry = handlerRegistry;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        String handlerName = "spring-trigger-task-" + System.currentTimeMillis();
        handlerRegistry.register(handlerName, param -> task.run());

        ScheduleTask scheduleTask = ScheduleTask.builder()
                .taskId(handlerName)
                .taskName("Spring Trigger Task")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(1000L)
                .build();

        localThreadTaskScheduler.schedule(scheduleTask);

        return new ScheduledFuture<Void>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                localThreadTaskScheduler.remove(handlerName);
                handlerRegistry.unregister(handlerName);
                return true;
            }

            @Override
            public boolean isCancelled() {
                return localThreadTaskScheduler.getTaskInfo(handlerName) == null;
            }

            @Override
            public boolean isDone() {
                return isCancelled();
            }

            @Override
            public Void get() {
                return null;
            }

            @Override
            public Void get(long timeout, TimeUnit unit) {
                return null;
            }

            @Override
            public long getDelay(TimeUnit unit) {
                return 0;
            }

            @Override
            public int compareTo(java.util.concurrent.Delayed other) {
                return 0;
            }
        };
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        String handlerName = "spring-delay-task-" + System.currentTimeMillis();
        handlerRegistry.register(handlerName, param -> task.run());

        long initialDelay = Duration.between(Instant.now(), startTime).toMillis();
        if (initialDelay < 0) {
            initialDelay = 0;
        }

        ScheduleTask scheduleTask = ScheduleTask.builder()
                .taskId(handlerName)
                .taskName("Spring Delay Task")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_DELAY)
                .delay(Long.MAX_VALUE)
                .initialDelay(initialDelay)
                .build();

        localThreadTaskScheduler.schedule(scheduleTask);

        return createScheduledFuture(handlerName);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
        String handlerName = "spring-fixed-rate-task-" + System.currentTimeMillis();
        handlerRegistry.register(handlerName, param -> task.run());

        long initialDelay = Duration.between(Instant.now(), startTime).toMillis();
        if (initialDelay < 0) {
            initialDelay = 0;
        }

        ScheduleTask scheduleTask = ScheduleTask.builder()
                .taskId(handlerName)
                .taskName("Spring Fixed Rate Task")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(period.toMillis())
                .initialDelay(initialDelay)
                .build();

        localThreadTaskScheduler.schedule(scheduleTask);

        return createScheduledFuture(handlerName);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
        String handlerName = "spring-fixed-rate-task-" + System.currentTimeMillis();
        handlerRegistry.register(handlerName, param -> task.run());

        ScheduleTask scheduleTask = ScheduleTask.builder()
                .taskId(handlerName)
                .taskName("Spring Fixed Rate Task")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(period.toMillis())
                .build();

        localThreadTaskScheduler.schedule(scheduleTask);

        return createScheduledFuture(handlerName);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
        String handlerName = "spring-fixed-delay-task-" + System.currentTimeMillis();
        handlerRegistry.register(handlerName, param -> task.run());

        long initialDelay = Duration.between(Instant.now(), startTime).toMillis();
        if (initialDelay < 0) {
            initialDelay = 0;
        }

        ScheduleTask scheduleTask = ScheduleTask.builder()
                .taskId(handlerName)
                .taskName("Spring Fixed Delay Task")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_DELAY)
                .delay(delay.toMillis())
                .initialDelay(initialDelay)
                .build();

        localThreadTaskScheduler.schedule(scheduleTask);

        return createScheduledFuture(handlerName);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
        String handlerName = "spring-fixed-delay-task-" + System.currentTimeMillis();
        handlerRegistry.register(handlerName, param -> task.run());

        ScheduleTask scheduleTask = ScheduleTask.builder()
                .taskId(handlerName)
                .taskName("Spring Fixed Delay Task")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_DELAY)
                .delay(delay.toMillis())
                .build();

        localThreadTaskScheduler.schedule(scheduleTask);

        return createScheduledFuture(handlerName);
    }

    private ScheduledFuture<Void> createScheduledFuture(String handlerName) {
        return new ScheduledFuture<Void>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                localThreadTaskScheduler.remove(handlerName);
                handlerRegistry.unregister(handlerName);
                return true;
            }

            @Override
            public boolean isCancelled() {
                return localThreadTaskScheduler.getTaskInfo(handlerName) == null;
            }

            @Override
            public boolean isDone() {
                return isCancelled();
            }

            @Override
            public Void get() {
                return null;
            }

            @Override
            public Void get(long timeout, TimeUnit unit) {
                return null;
            }

            @Override
            public long getDelay(TimeUnit unit) {
                return 0;
            }

            @Override
            public int compareTo(java.util.concurrent.Delayed other) {
                return 0;
            }
        };
    }
}