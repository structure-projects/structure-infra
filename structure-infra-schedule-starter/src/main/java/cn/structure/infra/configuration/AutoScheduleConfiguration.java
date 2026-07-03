package cn.structure.infra.configuration;

import cn.structure.infra.properties.ScheduleProperties;
import cn.structure.infra.schedule.DefaultTaskHandlerRegistry;
import cn.structure.infra.schedule.LocalThreadTaskScheduler;
import cn.structure.infra.schedule.SpringTaskSchedulerAdapter;
import cn.structure.infra.schedule.TaskHandlerRegistry;
import cn.structure.infra.schedule.TaskScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ScheduleProperties.class)
public class AutoScheduleConfiguration {

    @Bean
    @ConditionalOnMissingBean(TaskHandlerRegistry.class)
    public TaskHandlerRegistry taskHandlerRegistry() {
        return new DefaultTaskHandlerRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    public TaskScheduler taskScheduler(ScheduleProperties scheduleProperties, TaskHandlerRegistry handlerRegistry) {
        Integer poolSize = scheduleProperties.getPoolSize();
        return new LocalThreadTaskScheduler(poolSize != null ? poolSize : Runtime.getRuntime().availableProcessors(), handlerRegistry);
    }

    @Bean
    @ConditionalOnBean(LocalThreadTaskScheduler.class)
    public org.springframework.scheduling.TaskScheduler springTaskScheduler(LocalThreadTaskScheduler taskScheduler, TaskHandlerRegistry handlerRegistry) {
        return new SpringTaskSchedulerAdapter(taskScheduler, handlerRegistry);
    }
}