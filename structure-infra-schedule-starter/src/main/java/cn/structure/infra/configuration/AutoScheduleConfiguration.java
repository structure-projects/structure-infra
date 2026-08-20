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

/**
 * schedule-starter 的 Spring Boot 自动配置类。
 *
 * <p>该配置类负责装配本地调度体系的核心 Bean，包括：</p>
 * <ul>
 *     <li>{@link TaskHandlerRegistry}：handler 注册表（默认实现为 {@link DefaultTaskHandlerRegistry}）</li>
 *     <li>{@link TaskScheduler}：本地任务调度器（默认实现为 {@link LocalThreadTaskScheduler}）</li>
 *     <li>Spring 标准 {@link org.springframework.scheduling.TaskScheduler}：通过
 *         {@link SpringTaskSchedulerAdapter} 适配本地调度器，便于上层框架（如 Spring 自带调度）
 *         复用本地线程池</li>
 * </ul>
 *
 * <p><b>设计意图：</b>所有 Bean 均通过 {@code @ConditionalOnMissingBean} / {@code @ConditionalOnBean}
 * 进行条件装配，业务方可通过自定义 Bean 覆盖任一默认实现。同时，xxljob-starter 中的
 * {@code AutoXxlJobConfiguration} 通过 {@code @AutoConfigureBefore} 在本配置类之前装配，
 * 当 XXL-Job 启用时其 {@link TaskScheduler} Bean 会优先注册，从而覆盖本地实现。</p>
 */
@Configuration
@EnableConfigurationProperties(ScheduleProperties.class)
public class AutoScheduleConfiguration {

    /**
     * 装配默认的 handler 注册表。
     *
     * <p>仅当容器中不存在自定义 {@link TaskHandlerRegistry} 时生效。</p>
     *
     * @return 默认实现 {@link DefaultTaskHandlerRegistry}
     */
    @Bean
    @ConditionalOnMissingBean(TaskHandlerRegistry.class)
    public TaskHandlerRegistry taskHandlerRegistry() {
        return new DefaultTaskHandlerRegistry();
    }

    /**
     * 装配本地任务调度器。
     *
     * <p>仅当容器中不存在自定义 {@link TaskScheduler} 时生效。线程池大小取自
     * {@link ScheduleProperties#getPoolSize()}，若为空则回退到 JVM 可用处理器核数。</p>
     *
     * @param scheduleProperties 调度配置属性
     * @param handlerRegistry    handler 注册表，由调度器在执行任务时查找 handler
     * @return 本地调度器实例 {@link LocalThreadTaskScheduler}
     */
    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    public TaskScheduler taskScheduler(ScheduleProperties scheduleProperties, TaskHandlerRegistry handlerRegistry) {
        Integer poolSize = scheduleProperties.getPoolSize();
        return new LocalThreadTaskScheduler(poolSize != null ? poolSize : Runtime.getRuntime().availableProcessors(), handlerRegistry);
    }

    /**
     * 装配 Spring 标准 {@link org.springframework.scheduling.TaskScheduler} 适配器。
     *
     * <p>仅当容器中存在 {@link LocalThreadTaskScheduler} Bean 时生效，
     * 即仅本地调度生效时才提供 Spring 适配。若 XXL-Job 启用并覆盖了本地调度，
     * 则该 Bean 不会被创建。</p>
     *
     * @param taskScheduler    本地调度器实例
     * @param handlerRegistry  handler 注册表
     * @return Spring 标准 TaskScheduler 适配器
     */
    @Bean
    @ConditionalOnBean(LocalThreadTaskScheduler.class)
    public org.springframework.scheduling.TaskScheduler springTaskScheduler(LocalThreadTaskScheduler taskScheduler, TaskHandlerRegistry handlerRegistry) {
        return new SpringTaskSchedulerAdapter(taskScheduler, handlerRegistry);
    }
}