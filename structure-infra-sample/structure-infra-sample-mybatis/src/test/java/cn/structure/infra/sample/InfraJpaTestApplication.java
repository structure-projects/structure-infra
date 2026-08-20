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

package cn.structure.infra.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * JPA 测试启动类
 * <p>
 * 用于 JPA 场景测试，排除 MyBatis Plus 自动配置
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@SpringBootApplication(scanBasePackages = "cn.structure.infra.sample")
public class InfraJpaTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfraJpaTestApplication.class, args);
    }
}