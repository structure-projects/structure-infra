package cn.structure.infra.schedule.xxljob;

import cn.structure.infra.schedule.ScheduleTask;
import cn.structure.infra.schedule.TaskScheduler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 XXL-Job 的分布式任务调度器，实现 schedule-starter 模块的 {@link TaskScheduler} SPI。
 *
 * <p><b>设计意图：</b>将本地调度 SPI 透明地转发到 XXL-Job 分布式调度中心，使业务方
 * 在不修改调度 API 调用代码的前提下，从单机本地调度切换到分布式调度。当 xxljob-starter
 * 启用时（通过 {@code @AutoConfigureBefore} 机制），本类将覆盖本地
 * {@code LocalThreadTaskScheduler} 成为 {@link TaskScheduler} 的实现 Bean。</p>
 *
 * <p><b>核心机制：</b></p>
 * <ul>
 *     <li><b>taskId↔xxlJobId 双向映射</b>：业务侧以 taskId 操作，XXL-Job 侧以 xxlJobId
 *         操作，本类通过 {@link #taskIdToXxlJobIdMap} 维护 taskId 到 xxlJobId 的映射</li>
 *     <li><b>CRON 转换</b>：FIXED_RATE/FIXED_DELAY 类型会被转换为 XXL-Job 6 位 CRON
 *         表达式（{@code 0/N * * * * ?}），CRON 类型直接透传</li>
 *     <li><b>状态同步</b>：本地 {@link #taskMap} 维护任务元信息与状态，与 XXL-Job 侧状态保持一致</li>
 * </ul>
 *
 * <p><b>协作关系：</b>依赖 {@link XxlJobTemplate} 完成对 XXL-Job 调度中心的所有远程操作。</p>
 *
 * <p><b>CRON 转换说明：</b>XXL-Job 使用 6 位 CRON（秒 分 时 日 月 周），无年字段；
 * 本地 {@link ScheduleTask#getCronExpression()} 若为 7 位 CRON 含年字段，需调用方在
 * 传入前自行处理。本类对 CRON 类型直接透传，对 FIXED_RATE/FIXED_DELAY 通过
 * {@link #convertToCron(ScheduleTask)} 生成 6 位 CRON。</p>
 */
@Slf4j
public class XxlJobTaskScheduler implements TaskScheduler {

    /**
     * XXL-Job 操作模板，封装对调度中心的远程调用。
     */
    private final XxlJobTemplate xxlJobTemplate;

    /**
     * taskId 到 xxlJobId 的映射表。
     *
     * <p>这是 taskId↔xxlJobId 映射的核心数据结构：业务侧以 taskId 为操作单元，
     * 通过该 Map 查找对应的 XXL-Job 任务 ID 后再调用 {@link XxlJobTemplate} 完成远程操作。</p>
     */
    private final Map<String, String> taskIdToXxlJobIdMap = new ConcurrentHashMap<>();

    /**
     * taskId 到任务元信息的映射表，用于本地状态管理和查询。
     */
    private final Map<String, ScheduleTask> taskMap = new ConcurrentHashMap<>();

    /**
     * 构造方法。
     *
     * @param xxlJobTemplate XXL-Job 操作模板
     */
    public XxlJobTaskScheduler(XxlJobTemplate xxlJobTemplate) {
        this.xxlJobTemplate = xxlJobTemplate;
        log.info("XxlJobTaskScheduler initialized");
    }

    /**
     * 通过 XXL-Job 调度一个任务。
     *
     * <p>调度流程：</p>
     * <ol>
     *     <li>校验 task 字段</li>
     *     <li>若 taskId 已存在则先 remove 旧任务（含 XXL-Job 侧）</li>
     *     <li>将本地调度类型转换为 XXL-Job CRON 表达式</li>
     *     <li>调用 {@link XxlJobTemplate#add} 在 XXL-Job 侧创建任务</li>
     *     <li>建立 taskId→xxlJobId 映射，记录任务元信息，状态置为 RUNNING</li>
     * </ol>
     *
     * @param task 任务描述对象
     * @throws IllegalArgumentException 当 task 字段校验失败或 CRON 表达式为空时抛出
     * @throws RuntimeException         当 XXL-Job 远程调用失败时抛出
     */
    @Override
    public void schedule(ScheduleTask task) {
        validateTask(task);

        // 幂等调度：先移除同 taskId 的旧任务（含 XXL-Job 侧映射）
        remove(task.getTaskId());

        // CRON 转换：将本地调度类型转换为 XXL-Job 6 位 CRON 表达式
        String cronExpression = convertToCron(task);

        // 在 XXL-Job 调度中心添加任务，返回 xxlJobId
        String xxlJobId = xxlJobTemplate.add(
                task.getTaskName(),
                cronExpression,
                task.getHandlerName(),
                task.getHandlerParam()
        );

        // 建立 taskId→xxlJobId 映射，是 taskId↔xxlJobId 双向映射的核心数据
        taskIdToXxlJobIdMap.put(task.getTaskId(), xxlJobId);
        task.setStatus(ScheduleTask.TaskStatus.RUNNING);
        taskMap.put(task.getTaskId(), task);

        log.info("Scheduled task via XXL-Job: taskId={}, xxlJobId={}, handler={}",
                task.getTaskId(), xxlJobId, task.getHandlerName());
    }

    /**
     * 通过 XXL-Job 更新已有任务。
     *
     * <p>若 taskId→xxlJobId 映射不存在（说明任务未在 XXL-Job 侧注册），则降级为新增调度；
     * 否则通过映射查找 xxlJobId 并调用 {@link XxlJobTemplate#update}。</p>
     *
     * @param task 新的任务描述对象
     * @throws IllegalArgumentException 当 task 字段校验失败时抛出
     * @throws RuntimeException         当 XXL-Job 远程调用失败时抛出
     */
    @Override
    public void update(ScheduleTask task) {
        validateTask(task);

        // 通过 taskId→xxlJobId 映射查找 XXL-Job 侧任务 ID
        String xxlJobId = taskIdToXxlJobIdMap.get(task.getTaskId());
        if (xxlJobId == null) {
            // 映射不存在则降级为新增调度
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

    /**
     * 通过 XXL-Job 移除任务。
     *
     * <p>先通过 taskId→xxlJobId 映射查找 xxlJobId，再调用 {@link XxlJobTemplate#remove}
     * 删除 XXL-Job 侧任务，并清理本地映射和元信息，状态置为 STOPPED。</p>
     *
     * @param taskId 任务唯一标识
     */
    @Override
    public void remove(String taskId) {
        // 从映射中移除并获取 xxlJobId（taskId↔xxlJobId 映射清理）
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

    /**
     * 通过 XXL-Job 暂停任务调度。
     *
     * <p>通过 taskId→xxlJobId 映射查找后调用 {@link XxlJobTemplate#pause}，
     * 本地状态置为 PAUSED。映射关系保留以便恢复。</p>
     *
     * @param taskId 任务唯一标识
     */
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

    /**
     * 通过 XXL-Job 恢复任务调度。
     *
     * <p>仅当任务当前状态为 PAUSED 时才会调用 {@link XxlJobTemplate#start} 恢复调度。</p>
     *
     * @param taskId 任务唯一标识
     */
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

    /**
     * 校验任务字段合法性。
     *
     * <p>注意：与本地调度器不同，本实现不校验 handler 是否已注册——因为 handler 的实际
     * 执行发生在 XXL-Job 执行器侧，而非本进程。</p>
     *
     * @param task 待校验任务
     * @throws IllegalArgumentException 当 task、taskId、handlerName 为空或 scheduleType 为空时抛出
     */
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

    /**
     * 将本地 {@link ScheduleTask} 的调度类型转换为 XXL-Job CRON 表达式。
     *
     * <p><b>CRON 转换规则：</b></p>
     * <ul>
     *     <li>{@link ScheduleTask.ScheduleType#CRON}：直接透传 {@link ScheduleTask#getCronExpression()}，
     *         调用方需保证表达式为 XXL-Job 6 位 CRON 格式（秒 分 时 日 月 周，无年字段）</li>
     *     <li>{@link ScheduleTask.ScheduleType#FIXED_RATE} / {@link ScheduleTask.ScheduleType#FIXED_DELAY}：
     *         根据间隔毫秒数生成 6 位 CRON {@code 0/N * * * * ?}（每 N 秒触发一次），
     *         不足 1 秒按 1 秒处理</li>
     * </ul>
     *
     * @param task 任务描述对象
     * @return XXL-Job 6 位 CRON 表达式
     * @throws IllegalArgumentException 当 CRON 类型但表达式为空时抛出
     */
    private String convertToCron(ScheduleTask task) {
        // CRON 类型：直接透传，调用方需保证为 6 位 CRON（XXL-Job 无年字段）
        if (task.getScheduleType() == ScheduleTask.ScheduleType.CRON) {
            if (task.getCronExpression() == null || task.getCronExpression().isEmpty()) {
                throw new IllegalArgumentException("Cron expression cannot be null for CRON schedule type");
            }
            return task.getCronExpression();
        }

        // FIXED_RATE/FIXED_DELAY：取间隔毫秒数，FIXED_RATE 用 period，FIXED_DELAY 用 delay
        long milliseconds = task.getScheduleType() == ScheduleTask.ScheduleType.FIXED_RATE
                ? (task.getPeriod() != null ? task.getPeriod() : 1000L)
                : (task.getDelay() != null ? task.getDelay() : 1000L);

        // 毫秒转秒，不足 1 秒按 1 秒处理（XXL-Job CRON 秒级精度）
        long seconds = milliseconds / 1000;

        if (seconds < 1) {
            seconds = 1;
        }

        // 生成 6 位 CRON：0/N 表示从第 0 秒开始每 N 秒触发一次
        return "0/" + seconds + " * * * * ?";
    }
}