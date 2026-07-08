package cn.structure.infra.configuration;

import cn.structure.infra.properties.InfraProperties;
import cn.structure.infra.schedule.DefaultTaskHandlerRegistry;
import cn.structure.infra.schedule.LocalThreadTaskScheduler;
import cn.structure.infra.schedule.SpringTaskSchedulerAdapter;
import cn.structure.infra.schedule.TaskHandlerRegistry;
import cn.structure.infra.schedule.TaskScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 调度子系统自动装配配置类
 * <p>
 * 负责注册任务调度体系的核心组件，将框架自有的 {@link TaskScheduler} 与 Spring 的
 * {@link org.springframework.scheduling.TaskScheduler} 进行桥接。
 * <p>
 * 自动注册的 Bean：
 * <ul>
 *   <li>{@link TaskHandlerRegistry} —— 任务处理器注册表（仅当容器中缺失时）</li>
 *   <li>{@link TaskScheduler} —— 本地线程任务调度器（仅当容器中缺失时）</li>
 *   <li>{@link SpringTaskSchedulerAdapter} —— 适配 Spring 调度接口的桥接器</li>
 * </ul>
 * 线程池大小通过 {@link InfraProperties#getSchedulePoolSize()} 配置。
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Configuration
@EnableConfigurationProperties(InfraProperties.class)
public class AutoScheduleConfiguration {

    /**
     * 注册任务处理器注册表
     * <p>
     * 当容器中不存在 {@link TaskHandlerRegistry} 时使用默认实现 {@link DefaultTaskHandlerRegistry}
     *
     * @return 任务处理器注册表
     */
    @Bean
    @ConditionalOnMissingBean(TaskHandlerRegistry.class)
    public TaskHandlerRegistry taskHandlerRegistry() {
        return new DefaultTaskHandlerRegistry();
    }

    /**
     * 注册任务调度器
     * <p>
     * 默认使用基于本地线程池的 {@link LocalThreadTaskScheduler}，线程池大小取自
     * {@link InfraProperties#getSchedulePoolSize()}，未配置时回退到 CPU 核心数。
     *
     * @param infraProperties 框架配置属性
     * @param handlerRegistry 任务处理器注册表
     * @return 任务调度器实例
     */
    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    public TaskScheduler taskScheduler(InfraProperties infraProperties, TaskHandlerRegistry handlerRegistry) {
        Integer poolSize = infraProperties.getSchedulePoolSize();
        return new LocalThreadTaskScheduler(poolSize != null ? poolSize : Runtime.getRuntime().availableProcessors(), handlerRegistry);
    }

    /**
     * 注册 Spring 任务调度器适配器
     * <p>
     * 将框架自有的 {@link LocalThreadTaskScheduler} 适配为 Spring 标准的
     * {@link org.springframework.scheduling.TaskScheduler}，便于 @Scheduled 等场景复用。
     * 若自有调度器不是 {@link LocalThreadTaskScheduler} 类型，则返回 null 表示不适配。
     *
     * @param taskScheduler    框架自有任务调度器
     * @param handlerRegistry  任务处理器注册表
     * @return Spring 标准任务调度器适配器，无法适配时返回 null
     */
    @Bean
    @ConditionalOnMissingBean(name = "springTaskScheduler")
    public org.springframework.scheduling.TaskScheduler springTaskScheduler(TaskScheduler taskScheduler, TaskHandlerRegistry handlerRegistry) {
        if (taskScheduler instanceof LocalThreadTaskScheduler) {
            return new SpringTaskSchedulerAdapter((LocalThreadTaskScheduler) taskScheduler, handlerRegistry);
        }
        return null;
    }
}