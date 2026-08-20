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

import cn.structure.infra.properties.XxlJobProperties;
import cn.structure.infra.schedule.TaskScheduler;
import cn.structure.infra.schedule.xxljob.XxlJobTaskScheduler;
import cn.structure.infra.schedule.xxljob.XxlJobTemplate;
import cn.structure.infra.schedule.xxljob.XxlJobTemplateImpl;
import cn.structure.job.rpc.XxlJobClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * xxljob-starter 的 Spring Boot 自动配置类。
 *
 * <p>该配置类负责装配 XXL-Job 调度体系的核心 Bean，包括：</p>
 * <ul>
 *     <li>{@link XxlJobTemplate}：XXL-Job 操作模板（默认实现 {@link XxlJobTemplateImpl}）</li>
 *     <li>{@link TaskScheduler}：基于 XXL-Job 的 {@link XxlJobTaskScheduler}，
 *         覆盖本地 {@code LocalThreadTaskScheduler}</li>
 * </ul>
 *
 * <p><b>覆盖本地调度的关键机制：</b>通过 {@code @AutoConfigureBefore} 将本配置类
 * 排在 schedule-starter 的 {@link AutoScheduleConfiguration} <b>之前</b>装配。
 * 由于两个配置类都使用 {@code @ConditionalOnMissingBean(TaskScheduler.class)}，
 * 本类注册的 {@link XxlJobTaskScheduler} 会先占用 {@link TaskScheduler} Bean 位，
 * 使得 {@link AutoScheduleConfiguration} 中的本地调度器 Bean 不再创建，
 * 从而实现"XXL-Job 启用时覆盖本地调度"的效果。</p>
 *
 * <p><b>依赖说明：</b>本配置类依赖外部提供的 {@link XxlJobClient} Bean
 * （通常由 xxl-job-core 或项目内 xxljob-executor 模块自动装配）。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(XxlJobProperties.class)
@AutoConfigureBefore(cn.structure.infra.configuration.AutoScheduleConfiguration.class)
@ConditionalOnProperty(prefix = "structure.schedule.xxl-job", name = "enabled", havingValue = "true")
public class AutoXxlJobConfiguration {

    /**
     * 装配 XXL-Job 操作模板。
     *
     * <p>仅当容器中不存在自定义 {@link XxlJobTemplate} 时生效。</p>
     *
     * @param xxlJobClient     XXL-Job 远程调用客户端（外部提供）
     * @param xxlJobProperties XXL-Job 配置属性
     * @return 默认实现 {@link XxlJobTemplateImpl}
     */
    @Bean
    @ConditionalOnMissingBean(XxlJobTemplate.class)
    public XxlJobTemplate xxlJobTemplate(XxlJobClient xxlJobClient, XxlJobProperties xxlJobProperties) {
        log.info(">>>>>>>>>>> xxl-job template init.");
        return new XxlJobTemplateImpl(xxlJobClient, xxlJobProperties);
    }

    /**
     * 装配基于 XXL-Job 的 {@link TaskScheduler} 实现。
     *
     * <p>仅当容器中不存在自定义 {@link TaskScheduler} 时生效。由于本配置类通过
     * {@code @AutoConfigureBefore} 排在 {@link AutoScheduleConfiguration} 之前，
     * 此处注册的 {@link XxlJobTaskScheduler} 会优先占用 {@link TaskScheduler} Bean 位，
     * 从而阻止本地 {@code LocalThreadTaskScheduler} 的创建，实现 XXL-Job 覆盖本地调度。</p>
     *
     * @param xxlJobTemplate XXL-Job 操作模板
     * @return XXL-Job 任务调度器实例
     */
    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    public TaskScheduler taskScheduler(XxlJobTemplate xxlJobTemplate) {
        return new XxlJobTaskScheduler(xxlJobTemplate);
    }
}