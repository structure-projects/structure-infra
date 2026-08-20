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

package cn.structure.infra.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * schedule-starter 的配置属性类，前缀 {@code structure.schedule}。
 *
 * <p>该类承载本地调度器（{@link cn.structure.infra.schedule.LocalThreadTaskScheduler}）
 * 的可配置项，由 {@code AutoScheduleConfiguration} 通过
 * {@code @EnableConfigurationProperties} 装配并注入到调度器构造中。</p>
 *
 * <p><b>配置示例：</b></p>
 * <pre>
 * structure:
 *   schedule:
 *     pool-size: 8
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "structure.schedule")
public class ScheduleProperties {

    /**
     * 调度线程池大小。
     *
     * <p>该值将作为 {@link java.util.concurrent.ScheduledExecutorService} 的核心线程数，
     * 决定本地调度器可并行执行的任务数上限。若未显式配置，默认取 JVM 可用处理器核数
     * （{@code Runtime.getRuntime().availableProcessors()}）。</p>
     */
    private Integer poolSize = Runtime.getRuntime().availableProcessors();
}