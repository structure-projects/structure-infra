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

package cn.structure.infra.sample.mongodb.lowcode.config;

import cn.structure.infra.lowcode.configuration.LowCodeAutoConfiguration;
import cn.structure.infra.mongodb.lowcode.MongoLowCodeAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * MongoDB 低代码测试配置
 * <p>
 * 导入低代码仓储的自动配置，配合 MongoTestConfig 和 MockMongoConfiguration 使用。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@Configuration
@Import({
        LowCodeAutoConfiguration.class,
        MongoLowCodeAutoConfiguration.class
})
public class MongoLowCodeTestConfig {
}
