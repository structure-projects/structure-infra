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

package cn.structure.infra.sample.infra.repository;

import org.springframework.stereotype.Component;

/**
 * 用户仓储 MongoDB 实现
 * <p>
 * 继承基类 {@link AbstractUserRepositoryImpl}，使用 MongoDB 作为持久化技术。
 * <p>
 * 注意：通过命名约定和泛型参数自动匹配对应的 Delegate 实现，
 * 无需额外的 @Repository 注解配置。
 *
 * @author chuck
 * @version 1.0.2
 * @since 2026/6/28
 */
@Component("userRepository")
public class UserMongoRepositoryImpl extends AbstractUserRepositoryImpl {
}
