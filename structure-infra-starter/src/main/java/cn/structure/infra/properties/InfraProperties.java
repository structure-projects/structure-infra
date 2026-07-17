package cn.structure.infra.properties;

import cn.structure.infra.event.EventChannel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;



/**
 * 基础设施框架配置属性
 * <p>
 * 对应 YAML 配置前缀：{@code structure.infra}，集中管理事件、CQRS、调度等
 * 框架级参数。被 {@link cn.structure.infra.configuration.AutoEventConfiguration}、
 * {@link cn.structure.infra.configuration.AutoScheduleConfiguration} 等自动装配类引用。
 * <p>
 * 配置示例：
 * <pre>
 * structure:
 *   infra:
 *     default-event-channel: SPRING_EVENT
 *     cqrs: false
 *     schedule-pool-size: 8
 * </pre>
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "structure.infra")
public class InfraProperties {

    /**
     * 默认事件渠道类型
     * <p>
     * 当 {@link cn.structure.infra.event.Event} 声明为 {@link EventChannel#DEFAULT} 时，
     * 使用此配置决定实际发布方式
     *
     * @return 默认事件渠道
     */
    private EventChannel defaultEventChannel = EventChannel.SPRING_EVENT;

    /**
     * 是否全局开启 CQRS 读写分离
     *
     * @return true 表示开启
     */
    private Boolean cqrs = false;

    /**
     * 调度线程池大小，默认 CPU 核心数
     *
     * @return 调度线程池大小
     */
    private Integer schedulePoolSize = Runtime.getRuntime().availableProcessors();
}
