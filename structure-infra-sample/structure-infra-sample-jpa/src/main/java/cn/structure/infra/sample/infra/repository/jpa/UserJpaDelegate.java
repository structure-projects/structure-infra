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

package cn.structure.infra.sample.infra.repository.jpa;

import cn.structure.infra.jpa.repository.JpaRepositoryDelegate;
import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.infra.po.JpaUserPO;
import cn.structure.infra.sample.infra.repository.delegate.UserRepositoryDelegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户仓储 JPA 委托实现
 * <p>
 * 负责 UserEntity 与 UserPO 之间的转换，
 * 内部使用 JPA EntityManager 进行持久化操作。
 * <p>
 * 注意：通过命名约定和泛型参数自动匹配对应的 RepositoryFacade，
 * 无需额外的 @DelegateFor 注解配置。
 *
 * @author chuck
 * @version 1.0.2
 * @since 2026/6/28
 */
@Slf4j
@Component
public class UserJpaDelegate extends JpaRepositoryDelegate<UserEntity, JpaUserPO, Long> implements UserRepositoryDelegate {

    /**
     * 根据用户名查询用户
     * <p>
     * 使用 EntityManager 执行条件查询，将 PO 结果转换为 Entity 返回。
     *
     * @param name 用户名
     * @return 用户实体，不存在时返回 null
     */
    @Override
    public UserEntity findByName(String name) {
        UserEntity condition = new UserEntity();
        condition.setUsername(name);
        return queryOne(condition);
    }
}
