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

@Configuration
@EnableConfigurationProperties(InfraProperties.class)
public class AutoScheduleConfiguration {

    @Bean
    @ConditionalOnMissingBean(TaskHandlerRegistry.class)
    public TaskHandlerRegistry taskHandlerRegistry() {
        return new DefaultTaskHandlerRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    public TaskScheduler taskScheduler(InfraProperties infraProperties, TaskHandlerRegistry handlerRegistry) {
        Integer poolSize = infraProperties.getSchedulePoolSize();
        return new LocalThreadTaskScheduler(poolSize != null ? poolSize : Runtime.getRuntime().availableProcessors(), handlerRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(name = "springTaskScheduler")
    public org.springframework.scheduling.TaskScheduler springTaskScheduler(TaskScheduler taskScheduler, TaskHandlerRegistry handlerRegistry) {
        if (taskScheduler instanceof LocalThreadTaskScheduler) {
            return new SpringTaskSchedulerAdapter((LocalThreadTaskScheduler) taskScheduler, handlerRegistry);
        }
        return null;
    }
}