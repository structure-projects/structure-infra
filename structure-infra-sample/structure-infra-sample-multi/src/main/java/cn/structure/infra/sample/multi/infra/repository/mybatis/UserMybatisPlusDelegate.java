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

package cn.structure.infra.sample.multi.infra.repository.mybatis;

import cn.structure.infra.mybatis.plus.repository.MybatisPlusRepositoryDelegate;
import cn.structure.infra.sample.multi.domain.entity.UserEntity;
import cn.structure.infra.sample.multi.infra.mapper.UserMapper;
import cn.structure.infra.sample.multi.infra.po.MybatisUserPO;
import cn.structure.infra.sample.multi.infra.repository.delegate.UserRepositoryDelegate;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Primary
public class UserMybatisPlusDelegate extends MybatisPlusRepositoryDelegate<UserEntity, MybatisUserPO, Long> implements UserRepositoryDelegate {

    public UserMybatisPlusDelegate(UserMapper userMapper) {
        this.baseMapper = userMapper;
    }

    @Override
    public UserEntity findByName(String name) {
        List<MybatisUserPO> pos = baseMapper.selectList(Wrappers.<MybatisUserPO>lambdaQuery().eq(MybatisUserPO::getUsername, name));
        return pos != null && !pos.isEmpty() ? toEntity(pos.get(0)) : null;
    }
}