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

package cn.structure.infra.sample.domain.repository;

import cn.structure.common.repository.ICrudRepository;
import cn.structure.infra.sample.domain.entity.UserEntity;

/**
 * <p>
 * 用户仓储
 * </p>
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
public interface UserRepository extends ICrudRepository<UserEntity, Long> {

    UserEntity findByName(String name);
}
