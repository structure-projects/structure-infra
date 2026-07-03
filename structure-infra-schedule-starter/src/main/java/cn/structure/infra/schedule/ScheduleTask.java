package cn.structure.infra.schedule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.TimeUnit;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleTask {

    private String taskId;

    private String taskName;

    private String handlerName;

    private String handlerParam;

    private ScheduleType scheduleType;

    private String cronExpression;

    private Long initialDelay;

    private Long delay;

    private Long period;

    private TimeUnit timeUnit;

    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    public enum ScheduleType {
        CRON,
        FIXED_DELAY,
        FIXED_RATE
    }

    public enum TaskStatus {
        PENDING,
        RUNNING,
        PAUSED,
        STOPPED
    }
}