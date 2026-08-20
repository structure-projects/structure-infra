/*
Copyright 2023 Structure Projects

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	`http://www.apache.org/licenses/LICENSE-2.0`

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package cn.structure.infra.schedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 {@link ScheduledExecutorService} 的本地任务调度器默认实现。
 *
 * <p><b>设计意图：</b>提供单机环境下的轻量级任务调度能力，无需依赖外部组件
 * （如 Quartz、XXL-Job 调度中心），适用于中小规模应用或开发调试场景。
 * 通过 SPI 接口 {@link TaskScheduler} 暴露，可被 xxljob-starter 中的
 * {@code XxlJobTaskScheduler} 在分布式场景下覆盖。</p>
 *
 * <p><b>核心特性：</b></p>
 * <ul>
 *     <li><b>守护线程池</b>：创建的线程均为 daemon 线程，JVM 退出时不会阻塞</li>
 *     <li><b>错误隔离</b>：每个任务的执行都被 try-catch 包裹，单个任务的异常不会
 *         影响其他任务或导致调度线程死亡</li>
 *     <li><b>幂等调度</b>：{@link #schedule(ScheduleTask)} 在创建新任务前会先移除同 taskId 旧任务</li>
 *     <li><b>CRON 精确调度</b>：基于 Spring {@link CronExpression} 解析 CRON 表达式，
 *         通过递归一次性调度按下次触发时间精确触发，支持标准 6 字段 cron 语义（含 {@code ?} 字符）</li>
 * </ul>
 *
 * <p><b>协作关系：</b>依赖 {@link TaskHandlerRegistry} 完成 handler 查找；
 * 由 {@code AutoScheduleConfiguration} 在缺少自定义 {@link TaskScheduler} 时装配。</p>
 */
@Slf4j
public class LocalThreadTaskScheduler implements TaskScheduler {

    /**
     * 底层调度线程池，所有任务的触发由该线程池驱动。
     */
    private final ScheduledExecutorService executorService;

    /**
     * taskId 到其 {@link ScheduledFuture} 的映射，用于取消/暂停任务。
     */
    private final Map<String, ScheduledFuture<?>> futureMap = new ConcurrentHashMap<>();

    /**
     * taskId 到任务元信息的映射，用于查询任务信息和状态管理。
     */
    private final Map<String, ScheduleTask> taskMap = new ConcurrentHashMap<>();

    /**
     * handler 注册表，调度任务时按 handlerName 查找对应执行逻辑。
     */
    private final TaskHandlerRegistry handlerRegistry;

    /**
     * 构造方法，使用 JVM 默认可用处理器核数作为线程池大小。
     *
     * @param handlerRegistry handler 注册表
     */
    public LocalThreadTaskScheduler(TaskHandlerRegistry handlerRegistry) {
        this(Runtime.getRuntime().availableProcessors(), handlerRegistry);
    }

    /**
     * 构造方法，可指定线程池大小。
     *
     * <p>创建的线程均为守护线程（daemon），线程名前缀为 {@code structure-schedule-}。</p>
     *
     * @param poolSize        调度线程池核心线程数
     * @param handlerRegistry handler 注册表
     */
    public LocalThreadTaskScheduler(int poolSize, TaskHandlerRegistry handlerRegistry) {
        this.executorService = Executors.newScheduledThreadPool(poolSize, r -> {
            Thread thread = new Thread(r);
            thread.setName("structure-schedule-" + thread.getId());
            thread.setDaemon(true);  // 守护线程：JVM 退出时自动结束，避免阻塞应用关闭
            return thread;
        });
        this.handlerRegistry = handlerRegistry;
        log.info("LocalThreadTaskScheduler initialized with pool size: {}", poolSize);
    }

    /**
     * 调度一个任务。
     *
     * <p>调度流程：</p>
     * <ol>
     *     <li>校验 task 字段（taskId、handlerName、handler 是否注册、scheduleType）</li>
     *     <li>移除同 taskId 的旧任务（实现幂等调度）</li>
     *     <li>将 handler 执行包装为带错误隔离的 Runnable</li>
     *     <li>按 {@link ScheduleTask.ScheduleType} 选择对应的调度策略</li>
     *     <li>记录 future 和 task，状态置为 {@code RUNNING}</li>
     * </ol>
     *
     * @param task 任务描述对象
     * @throws IllegalArgumentException 当 task 字段校验失败或 scheduleType 不支持时抛出
     */
    @Override
    public void schedule(ScheduleTask task) {
        validateTask(task);

        // 幂等调度：先移除同 taskId 的旧任务，避免重复调度
        remove(task.getTaskId());

        ScheduledFuture<?> future;
        // 包装为带错误隔离的 Runnable，防止单个任务异常影响其他任务
        Runnable wrappedRunnable = wrapRunnable(task);

        switch (task.getScheduleType()) {
            case FIXED_DELAY:
                // 固定延迟：上次执行结束 → 等待 delay → 触发下次
                long delay = task.getDelay() != null ? task.getDelay() : 1000L;
                long initialDelay = task.getInitialDelay() != null ? task.getInitialDelay() : 0L;
                TimeUnit timeUnit = task.getTimeUnit() != null ? task.getTimeUnit() : TimeUnit.MILLISECONDS;
                future = executorService.scheduleWithFixedDelay(wrappedRunnable, initialDelay, delay, timeUnit);
                break;

            case FIXED_RATE:
                // 固定频率：按固定间隔触发，与上次执行耗时无关
                long period = task.getPeriod() != null ? task.getPeriod() : 1000L;
                initialDelay = task.getInitialDelay() != null ? task.getInitialDelay() : 0L;
                timeUnit = task.getTimeUnit() != null ? task.getTimeUnit() : TimeUnit.MILLISECONDS;
                future = executorService.scheduleAtFixedRate(wrappedRunnable, initialDelay, period, timeUnit);
                break;

            case CRON:
                // 基于 Spring CronExpression 解析 cron 表达式，按下次触发时间递归调度
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

    /**
     * 更新已有任务的调度配置。
     *
     * <p>内部实现为"先查后调度"，若 taskId 不存在则仅记录警告日志；
     * 若存在则调用 {@link #schedule(ScheduleTask)} 重新调度（schedule 内部会先 remove 旧任务）。</p>
     *
     * @param task 新的任务描述对象，taskId 必须与已存在任务一致
     * @throws IllegalArgumentException 当 task 字段校验失败时抛出
     */
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

    /**
     * 校验任务字段合法性。
     *
     * @param task 待校验任务
     * @throws IllegalArgumentException 当 task、taskId、handlerName 为空，
     *                                  handler 未注册或 scheduleType 为空时抛出
     */
    private void validateTask(ScheduleTask task) {
        if (task == null || task.getTaskId() == null) {
            throw new IllegalArgumentException("Task and taskId cannot be null");
        }

        if (task.getHandlerName() == null || task.getHandlerName().isEmpty()) {
            throw new IllegalArgumentException("Handler name cannot be null or empty");
        }

        // 校验 handler 是否已在注册表中注册
        if (!handlerRegistry.contains(task.getHandlerName())) {
            throw new IllegalArgumentException("Handler not found: " + task.getHandlerName());
        }

        if (task.getScheduleType() == null) {
            throw new IllegalArgumentException("ScheduleType cannot be null");
        }
    }

    /**
     * CRON 任务的精确调度实现。
     *
     * <p><b>实现原理：</b>使用 Spring {@link CronExpression} 解析 cron 表达式，
     * 采用"递归一次性调度"模式——每次执行完成后，根据 cron 表达式计算下次触发时间，
     * 通过 {@link ScheduledExecutorService#schedule(Runnable, long, TimeUnit)} 安排下一次执行，
     * 如此循环直至任务被取消。</p>
     *
     * <p>相比固定频率轮询，该方式能严格遵循 cron 语义（如 {@code 0 0 12 * * ?} 每天 12 点触发），
     * 不会出现"每秒都触发"的问题。支持标准 6 字段 cron 表达式（秒 分 时 日 月 周），
     * day-of-month / day-of-week 字段可使用 {@code ?} 表示不指定。</p>
     *
     * <p>错误隔离由外层 {@link #wrapRunnable(ScheduleTask)} 保证，本方法仅负责调度编排。
     * 即便 handler 抛出异常，finally 块仍会安排下次执行，确保周期性调度不中断。</p>
     *
     * @param task            任务描述对象
     * @param wrappedRunnable 已包装错误隔离的 Runnable
     * @return 可取消的调度 future，cancel 时会终止整个调度链
     * @throws IllegalArgumentException 当 cron 表达式非法或不存在下次触发时间时抛出
     */
    private ScheduledFuture<?> scheduleCronTask(ScheduleTask task, Runnable wrappedRunnable) {
        CronExpression cronExpression;
        try {
            cronExpression = CronExpression.parse(task.getCronExpression());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cron expression: " + task.getCronExpression(), e);
        }

        // 取消标志：cancel 后阻止调度链继续递归
        AtomicBoolean cancelled = new AtomicBoolean(false);
        // 持有当前一次性调度的 future，便于取消整个调度链中的最近一次待触发任务
        AtomicReference<ScheduledFuture<?>> currentFutureRef = new AtomicReference<>();

        // 递归调度：每次执行完成后根据 cron 计算下次触发时间并安排一次性调度
        Runnable cronRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    wrappedRunnable.run();
                } finally {
                    // handler 异常已被 wrapRunnable 隔离，这里仍确保调度下一次
                    if (!cancelled.get()) {
                        scheduleNext();
                    }
                }
            }

            private void scheduleNext() {
                if (cancelled.get()) {
                    return;
                }
                long delayMs = computeNextDelayMs(cronExpression);
                if (delayMs < 0) {
                    // 没有下次触发时间，停止递归
                    log.warn("Cron task has no next execution time, stopping: id={}, cron={}",
                            task.getTaskId(), task.getCronExpression());
                    return;
                }
                ScheduledFuture<?> nextFuture = executorService.schedule(this, delayMs, TimeUnit.MILLISECONDS);
                currentFutureRef.set(nextFuture);
                // 二次检查：防止在 schedule 与 cancel 并发时漏取消
                if (cancelled.get()) {
                    nextFuture.cancel(false);
                }
            }
        };

        // 计算首次触发时间
        long initialDelayMs = computeNextDelayMs(cronExpression);
        if (initialDelayMs < 0) {
            throw new IllegalArgumentException(
                    "Cron expression has no valid next execution time: " + task.getCronExpression());
        }

        ScheduledFuture<?> initialFuture = executorService.schedule(cronRunnable, initialDelayMs, TimeUnit.MILLISECONDS);
        currentFutureRef.set(initialFuture);

        log.info("Scheduled cron task: id={}, cron={}, firstFireIn={}ms",
                task.getTaskId(), task.getCronExpression(), initialDelayMs);

        return new CronScheduledFuture(cancelled, currentFutureRef);
    }

    /**
     * 根据 cron 表达式计算从当前时刻到下次触发时刻的延迟（毫秒）。
     *
     * <p><b>边界处理（防止重复触发）：</b></p>
     * <ul>
     *     <li>确保返回的 next 严格晚于 now——若 {@link CronExpression#next} 在秒边界处
     *         返回了不晚于 now 的时刻，则继续向后推进到下一个匹配点，避免零延迟重排</li>
     *     <li>亚毫秒级延迟向上取整为 1ms，避免 {@code Duration.toMillis()} 截断为 0
     *         导致任务立即重排、与上一次触发落在同一毫秒</li>
     * </ul>
     *
     * @param cronExpression 已解析的 cron 表达式
     * @return 下次触发的延迟毫秒数（≥1）；若无下次触发时间则返回 -1
     */
    private static long computeNextDelayMs(CronExpression cronExpression) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = cronExpression.next(now);
        while (next != null && !next.isAfter(now)) {
            next = cronExpression.next(next);
        }
        if (next == null) {
            return -1L;
        }
        long delayMs = Duration.between(now, next).toMillis();
        if (delayMs <= 0) {
            delayMs = 1;
        }
        return delayMs;
    }

    /**
     * CRON 调度链的 {@link ScheduledFuture} 包装器。
     *
     * <p>由于 CRON 任务采用递归一次性调度，底层 future 会随每次触发而更换。
     * 本包装器持有取消标志与当前 future 的原子引用，cancel 时设置标志并取消
     * 当前待触发的 future，从而终止整个调度链。</p>
     */
    private static final class CronScheduledFuture implements ScheduledFuture<Void> {

        private final AtomicBoolean cancelled;
        private final AtomicReference<ScheduledFuture<?>> currentFutureRef;

        CronScheduledFuture(AtomicBoolean cancelled, AtomicReference<ScheduledFuture<?>> currentFutureRef) {
            this.cancelled = cancelled;
            this.currentFutureRef = currentFutureRef;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled.set(true);
            ScheduledFuture<?> current = currentFutureRef.get();
            if (current != null) {
                return current.cancel(mayInterruptIfRunning);
            }
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public boolean isDone() {
            return cancelled.get();
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
            ScheduledFuture<?> current = currentFutureRef.get();
            return current != null ? current.getDelay(unit) : 0;
        }

        @Override
        public int compareTo(Delayed o) {
            if (o instanceof CronScheduledFuture) {
                CronScheduledFuture other = (CronScheduledFuture) o;
                ScheduledFuture<?> current = currentFutureRef.get();
                ScheduledFuture<?> otherCurrent = other.currentFutureRef.get();
                if (current != null && otherCurrent != null) {
                    return current.compareTo(otherCurrent);
                }
            }
            ScheduledFuture<?> current = currentFutureRef.get();
            return current != null ? current.compareTo(o) : -1;
        }
    }

    /**
     * 将任务执行包装为带错误隔离的 Runnable。
     *
     * <p>错误隔离核心：通过 try-catch 捕获 handler 执行过程中的所有异常，
     * 仅记录日志不向上抛出，确保单个任务异常不会影响其他任务的调度。</p>
     *
     * @param task 任务描述对象
     * @return 包装后的 Runnable
     */
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
                // 错误隔离：捕获 handler 执行异常，避免影响调度线程和其他任务
                log.error("Task execution failed: id={}, name={}, handler={}, error={}", task.getTaskId(), task.getTaskName(), task.getHandlerName(), e.getMessage(), e);
            }
        };
    }

    /**
     * 移除任务并停止其调度。
     *
     * <p>取消对应 future（不中断已运行任务），从映射中移除，并将任务状态置为 {@code STOPPED}。</p>
     *
     * @param taskId 任务唯一标识
     */
    @Override
    public void remove(String taskId) {
        ScheduledFuture<?> future = futureMap.remove(taskId);
        if (future != null) {
            // false：不中断正在执行的任务，等其自然结束
            future.cancel(false);
        }

        ScheduleTask task = taskMap.remove(taskId);
        if (task != null) {
            task.setStatus(ScheduleTask.TaskStatus.STOPPED);
        }

        log.info("Removed task: id={}", taskId);
    }

    /**
     * 暂停任务调度。
     *
     * <p>取消对应 future，但任务信息仍保留在 {@link #taskMap} 中，状态置为 {@code PAUSED}，
     * 可通过 {@link #resume(String)} 恢复。</p>
     *
     * @param taskId 任务唯一标识
     */
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

    /**
     * 恢复被暂停的任务调度。
     *
     * <p>仅当任务当前状态为 {@code PAUSED} 时才会重新调度，否则忽略。</p>
     *
     * @param taskId 任务唯一标识
     */
    @Override
    public void resume(String taskId) {
        ScheduleTask task = taskMap.get(taskId);
        if (task != null && task.getStatus() == ScheduleTask.TaskStatus.PAUSED) {
            // 重新调度即可，schedule 内部会先 remove 旧的 future 再创建新的
            schedule(task);
            log.info("Resumed task: id={}", taskId);
        }
    }

    /**
     * 根据任务 ID 查询任务信息。
     *
     * @param taskId 任务唯一标识
     * @return 任务描述对象；若任务不存在则返回 {@code null}
     */
    @Override
    public ScheduleTask getTaskInfo(String taskId) {
        return taskMap.get(taskId);
    }

    /**
     * 获取当前调度器中所有已注册任务的快照列表。
     *
     * @return 任务列表的不可变副本；若无任何任务则返回空列表
     */
    @Override
    public List<ScheduleTask> getAllTasks() {
        return List.copyOf(taskMap.values());
    }
}