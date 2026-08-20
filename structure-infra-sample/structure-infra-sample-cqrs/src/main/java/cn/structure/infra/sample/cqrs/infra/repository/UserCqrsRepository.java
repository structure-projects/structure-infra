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

package cn.structure.infra.sample.cqrs.infra.repository;

import cn.structure.infra.repository.CqrsRepositoryFacade;
import cn.structure.infra.sample.cqrs.infra.delegate.read.UserReadDelegate;
import cn.structure.infra.sample.cqrs.infra.delegate.write.UserWriteDelegate;
import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.domain.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * 用户 CQRS 仓储实现
 * <p>
 * 继承 CqrsRepositoryFacade，实现读写分离模式：
 * - 写操作通过 UserWriteDelegate（MyBatis Plus）执行
 * - 读操作通过 UserReadDelegate（Elasticsearch）执行
 * - 读操作失败时自动回退到写代理
 * <p>
 * 注意：通过泛型参数和 @ReadDelegate 注解自动匹配对应的代理实现，
 * 无需额外的 @Repository 注解配置。
 *
 * @author chuck
 * @version 1.0.2
 * @since 2026/6/28
 */
@Component("userCqrsRepository")
public class UserCqrsRepository extends CqrsRepositoryFacade<UserEntity, Long, UserWriteDelegate, UserReadDelegate> implements UserRepository {

    @Override
    public UserEntity findByName(String name) {
        return getReadDelegate().findByName(name);
    }
}