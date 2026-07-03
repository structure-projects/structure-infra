package cn.structure.infra.schedule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.TimeUnit;

/**
 * 调度任务 POJO，用于描述一个待调度任务的完整元信息。
 *
 * <p>该类是 {@link TaskScheduler} 与业务方之间的数据载体，业务方通过 Builder 模式
 * 构造任务描述后提交给调度器，调度器再依据其中的 {@link ScheduleType}、cron 表达式、
 * 延迟参数等触发实际调度。</p>
 *
 * <p><b>设计意图：</b>使用不可变数据模型 + Builder 模式，统一封装不同调度语义
 * （CRON、固定频率、固定延迟）所需参数，调用方按需填充对应字段即可。</p>
 *
 * <p><b>CRON 限制说明：</b>本地 {@link LocalThreadTaskScheduler} 实现 CRON 调度时
 * 采用 1 秒级粒度的轮询策略（不支持秒级以下精度），即 CRON 表达式最小触发单位为秒。
 * 若需要更精细的调度请改用 {@link ScheduleType#FIXED_RATE} 或 {@link ScheduleType#FIXED_DELAY}。</p>
 *
 * <p><b>字段使用约定：</b></p>
 * <ul>
 *     <li>当 {@link #scheduleType} = {@link ScheduleType#CRON} 时，使用 {@link #cronExpression}</li>
 *     <li>当 {@link #scheduleType} = {@link ScheduleType#FIXED_DELAY} 时，使用 {@link #delay}（+可选 {@link #initialDelay}）</li>
 *     <li>当 {@link #scheduleType} = {@link ScheduleType#FIXED_RATE} 时，使用 {@link #period}（+可选 {@link #initialDelay}）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleTask {

    /**
     * 任务唯一标识。
     *
     * <p>调度器以该字段作为 key 维护任务映射，相同 taskId 重复调度会被视为更新。</p>
     */
    private String taskId;

    /**
     * 任务名称（描述性信息），用于日志展示和监控。
     */
    private String taskName;

    /**
     * 处理器名称，对应 {@link TaskHandlerRegistry} 中注册的 key。
     *
     * <p>调度前会校验该名称是否已注册，未注册将抛出异常。</p>
     */
    private String handlerName;

    /**
     * 处理器执行参数，将在 {@link TaskHandler#execute(String)} 中传入。
     */
    private String handlerParam;

    /**
     * 调度类型，决定调度器使用哪种触发策略。
     */
    private ScheduleType scheduleType;

    /**
     * CRON 表达式，仅在 {@link #scheduleType} = {@link ScheduleType#CRON} 时使用。
     *
     * <p>本地实现为秒级粒度轮询，不支持秒级以下精度。</p>
     */
    private String cronExpression;

    /**
     * 初始延迟（配合 {@link TimeUnit} 使用），用于 FIXED_DELAY / FIXED_RATE 场景。
     */
    private Long initialDelay;

    /**
     * 固定延迟间隔，仅在 {@link #scheduleType} = {@link ScheduleType#FIXED_DELAY} 时使用。
     */
    private Long delay;

    /**
     * 固定频率间隔，仅在 {@link #scheduleType} = {@link ScheduleType#FIXED_RATE} 时使用。
     */
    private Long period;

    /**
     * 时间单位，作用于 {@link #initialDelay} / {@link #delay} / {@link #period}，默认毫秒。
     */
    private TimeUnit timeUnit;

    /**
     * 任务状态，默认 {@link TaskStatus#PENDING}，由调度器在生命周期变化时更新。
     */
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    /**
     * 调度类型枚举。
     *
     * <ul>
     *     <li>{@link #CRON}：基于 CRON 表达式（本地实现为秒级粒度轮询）</li>
     *     <li>{@link #FIXED_DELAY}：固定延迟（上次执行结束后等待 delay 再触发下次）</li>
     *     <li>{@link #FIXED_RATE}：固定频率（按固定间隔触发，与上次执行耗时无关）</li>
     * </ul>
     */
    public enum ScheduleType {
        CRON,
        FIXED_DELAY,
        FIXED_RATE
    }

    /**
     * 任务状态枚举。
     *
     * <ul>
     *     <li>{@link #PENDING}：已创建但尚未调度</li>
     *     <li>{@link #RUNNING}：已注册到调度器并处于运行中</li>
     *     <li>{@link #PAUSED}：已暂停，可恢复</li>
     *     <li>{@link #STOPPED}：已停止/移除</li>
     * </ul>
     */
    public enum TaskStatus {
        PENDING,
        RUNNING,
        PAUSED,
        STOPPED
    }
}