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

package cn.structure.infra.sample.lowcode;

import cn.structure.infra.sample.config.MybatisOnlyConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 低代码仓储测试配置
 * <p>
 * 基于 MybatisOnlyConfig，额外导入低代码仓储的自动配置。
 * 放在独立包下，避免被 MybatisOnlyConfig 的 ComponentScan 扫描导致 Bean 冲突。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@Configuration
@Import({
        MybatisOnlyConfig.class,
        cn.structure.infra.lowcode.configuration.LowCodeAutoConfiguration.class,
        cn.structure.infra.mybatis.plus.lowcode.configuration.MybatisPlusLowCodeAutoConfiguration.class
})
public class LowCodeTestConfig {
}
