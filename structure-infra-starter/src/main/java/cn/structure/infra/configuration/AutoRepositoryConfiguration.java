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

import cn.structure.infra.repository.MultiRepositoryBeanPostProcessor;
import cn.structure.infra.repository.RepositoryBeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 仓储自动装配配置类
 * <p>
 * 负责注册仓储框架的核心组件。
 * <p>
 * 核心功能：
 * 1. RepositoryDelegate 通过 RepositoryBeanPostProcessor 自动注入到 RepositoryFacade
 * 2. 支持 CQRS 模式，通过 @WriteDelegate 和 @ReadDelegate 注解区分读写代理
 * 3. 写代理注入优先级：@WriteDelegate 标记 > 无 @ReadDelegate 标记 > 任意匹配
 * 4. 读代理注入优先级：@ReadDelegate 标记 > 任意匹配
 * 5. 当找不到匹配的 Delegate 时，由各存储模块的 AutoConfiguration 通过 @ConditionalOnMissingBean 提供默认实现
 *
 * @author chuck
 * @version 1.0.3
 * @since 2026/6/28
 */
@Configuration
public class AutoRepositoryConfiguration {

    @Bean
    public RepositoryBeanPostProcessor repositoryBeanPostProcessor() {
        return new RepositoryBeanPostProcessor();
    }

    @Bean
    public MultiRepositoryBeanPostProcessor multiRepositoryBeanPostProcessor() {
        return new MultiRepositoryBeanPostProcessor();
    }
}