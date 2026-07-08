package cn.structure.infra.schedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Spring 标准 {@link TaskScheduler} 适配器，将 Spring 的调度 API 转发到本地
 * {@link LocalThreadTaskScheduler}。
 *
 * <p><b>设计意图：</b>Spring 框架本身定义了一套 {@link org.springframework.scheduling.TaskScheduler}
 * 抽象（用于 {@code @Scheduled}、{@code @EnableScheduling} 等机制）。为了让上层使用 Spring
 * 调度 API 的代码能够无缝复用本模块的本地线程池调度能力，本适配器将 Spring 接口的方法
 * 翻译为 {@link ScheduleTask} 并委托给 {@link LocalThreadTaskScheduler} 执行。</p>
 *
 * <p><b>协作关系：</b>由 {@code AutoScheduleConfiguration} 在容器中存在
 * {@link LocalThreadTaskScheduler} Bean 时装配。每个 Spring 调度请求都会被转换为一个
 * 临时 handler（注册到 {@link TaskHandlerRegistry}），再以 {@link ScheduleTask} 形式提交给
 * 本地调度器。</p>
 *
 * <p><b>限制说明：</b>返回的 {@link ScheduledFuture} 为简化实现，{@code get()}、
 * {@code getDelay()} 等方法返回固定值，仅用于满足 Spring 接口约定及支持取消/状态查询。</p>
 */
@Slf4j
public class SpringTaskSchedulerAdapter implements TaskScheduler {

    /**
     * 被适配的本地调度器。
     */
    private final LocalThreadTaskScheduler localThreadTaskScheduler;

    /**
     * handler 注册表，用于注册临时转换出来的 handler。
     */
    private final TaskHandlerRegistry handlerRegistry;

    /**
     * 构造方法。
     *
     * @param localThreadTaskScheduler 被适配的本地调度器
     * @param handlerRegistry          handler 注册表
     */
    public SpringTaskSchedulerAdapter(LocalThreadTaskScheduler localThreadTaskScheduler,
                                     TaskHandlerRegistry handlerRegistry) {
        this.localThreadTaskScheduler = localThreadTaskScheduler;
        this.handlerRegistry = handlerRegistry;
    }

    /**
     * 基于 {@link Trigger} 的调度（Spring 接口方法）。
     *
     * <p>由于本地调度器不支持 Trigger 语义，此处简化为固定 1 秒频率触发。</p>
     *
     * @param task    待执行的 Runnable
     * @param trigger 触发器（本实现未真正解析，仅做简化处理）
     * @return 可用于取消的 ScheduledFuture
     */
    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        // 生成唯一 handlerName，避免与业务 handler 冲突
        String handlerName = "spring-trigger-task-" + System.currentTimeMillis();
        // 将 Runnable 包装为 TaskHandler 注册
        handlerRegistry.register(handlerName, param -> task.run());

        ScheduleTask scheduleTask = ScheduleTask.builder()
                .taskId(handlerName)
                .taskName("Spring Trigger Task")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(1000L)
                .build();

        localThreadTaskScheduler.schedule(scheduleTask);

        // 返回简化的 ScheduledFuture，cancel 时联动移除任务和 handler
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

    /**
     * 在指定时间点触发一次的任务调度（Spring 接口方法）。
     *
     * <p>实现为 FIXED_DELAY，并将 delay 设为 {@code Long.MAX_VALUE} 使其仅触发一次。</p>
     *
     * @param task      待执行的 Runnable
     * @param startTime 触发时间点
     * @return 可用于取消的 ScheduledFuture
     */
    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        String handlerName = "spring-delay-task-" + System.currentTimeMillis();
        handlerRegistry.register(handlerName, param -> task.run());

        // 计算初始延迟，若已过期则立即触发
        long initialDelay = Duration.between(Instant.now(), startTime).toMillis();
        if (initialDelay < 0) {
            initialDelay = 0;
        }

        ScheduleTask scheduleTask = ScheduleTask.builder()
                .taskId(handlerName)
                .taskName("Spring Delay Task")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_DELAY)
                .delay(Long.MAX_VALUE)  // 仅触发一次：delay 设为极大值
                .initialDelay(initialDelay)
                .build();

        localThreadTaskScheduler.schedule(scheduleTask);

        return createScheduledFuture(handlerName);
    }

    /**
     * 以固定频率触发任务，可指定起始时间（Spring 接口方法）。
     *
     * @param task      待执行的 Runnable
     * @param startTime 起始时间点
     * @param period    触发间隔
     * @return 可用于取消的 ScheduledFuture
     */
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

    /**
     * 以固定频率触发任务，立即开始（Spring 接口方法）。
     *
     * @param task   待执行的 Runnable
     * @param period 触发间隔
     * @return 可用于取消的 ScheduledFuture
     */
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

    /**
     * 以固定延迟触发任务，可指定起始时间（Spring 接口方法）。
     *
     * @param task      待执行的 Runnable
     * @param startTime 起始时间点
     * @param delay     每次执行结束后的延迟间隔
     * @return 可用于取消的 ScheduledFuture
     */
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

    /**
     * 以固定延迟触发任务，立即开始（Spring 接口方法）。
     *
     * @param task  待执行的 Runnable
     * @param delay 每次执行结束后的延迟间隔
     * @return 可用于取消的 ScheduledFuture
     */
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

    /**
     * 创建简化的 {@link ScheduledFuture}，仅支持取消和状态查询。
     *
     * <p>cancel 时联动移除本地调度器中的任务和注册表中的 handler，避免资源泄漏。</p>
     *
     * @param handlerName 临时 handler 名称（同时也是 taskId）
     * @return 简化的 ScheduledFuture 实例
     */
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