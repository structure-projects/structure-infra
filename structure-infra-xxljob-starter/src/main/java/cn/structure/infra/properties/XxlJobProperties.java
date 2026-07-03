package cn.structure.infra.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * xxljob-starter 的配置属性类，前缀 {@code structure.schedule.xxl-job}。
 *
 * <p>该类承载 XXL-Job 集成相关的可配置项，由 {@code AutoXxlJobConfiguration} 通过
 * {@code @EnableConfigurationProperties} 装配，并注入到 {@code XxlJobTemplateImpl} 等组件中。</p>
 *
 * <p><b>配置示例：</b></p>
 * <pre>
 * structure:
 *   schedule:
 *     xxl-job:
 *       enabled: true
 *       job-group: 1
 * </pre>
 *
 * <p><b>启用机制说明：</b>当 {@link #enabled} 为 {@code true} 时，{@code AutoXxlJobConfiguration}
 * 会注册 {@code XxlJobTaskScheduler} 作为 {@link cn.structure.infra.schedule.TaskScheduler} SPI
 * 的实现，并通过 {@code @AutoConfigureBefore} 在本地 {@code AutoScheduleConfiguration} 之前装配，
 * 从而使 XXL-Job 实现覆盖本地调度实现。</p>
 */
@Data
@ConfigurationProperties(prefix = "structure.schedule.xxl-job")
public class XxlJobProperties {

    /**
     * 是否启用 XXL-Job 调度体系。
     *
     * <p>启用后，{@code XxlJobTaskScheduler} 将覆盖本地 {@code LocalThreadTaskScheduler}
     * 成为 {@link cn.structure.infra.schedule.TaskScheduler} 的实现 Bean。</p>
     */
    private boolean enabled = true;

    /**
     * XXL-Job 的执行器分组 ID。
     *
     * <p>对应 XXL-Job 调度中心中的 jobGroup，用于将任务归属到特定执行器分组下。
     * 默认值为 {@code 1}，需根据实际调度中心配置调整。</p>
     */
    private Integer jobGroup = 1;

}