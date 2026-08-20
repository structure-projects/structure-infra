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

package cn.structure.infra.sample.cqrs.infra.delegate.write;

import cn.structure.infra.annotations.WriteDelegate;
import cn.structure.infra.mybatis.plus.repository.MybatisPlusRepositoryDelegate;
import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.infra.mapper.UserMapper;
import cn.structure.infra.sample.infra.po.MybatisUserPO;
import cn.structure.infra.sample.infra.repository.delegate.UserRepositoryDelegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 用户写代理（BASE）
 * <p>
 * 模拟数据库写操作，负责处理所有写操作：
 * - save
 * - removeById
 * - saveBatch
 * - removeBatchByIds
 * <p>
 * 同时也可以处理读操作（作为读操作的兜底）
 * <p>
 * 使用 @WriteDelegate 注解标记为写代理，便于 RepositoryBeanPostProcessor 精准注入
 */
@Slf4j
@Component
@Primary
@WriteDelegate
public class UserWriteDelegate extends MybatisPlusRepositoryDelegate<UserEntity, MybatisUserPO, Long> implements UserRepositoryDelegate {

    public UserWriteDelegate(UserMapper userMapper) {
        this.baseMapper = userMapper;
    }

    @Override
    public UserEntity findByName(String name) {
        return null;
    }
}
