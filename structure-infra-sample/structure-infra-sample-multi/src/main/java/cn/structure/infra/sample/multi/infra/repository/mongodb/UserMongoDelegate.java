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

package cn.structure.infra.sample.multi.infra.repository.mongodb;

import cn.structure.infra.mongodb.repository.MongoRepositoryDelegate;
import cn.structure.infra.sample.multi.domain.entity.UserEntity;
import cn.structure.infra.sample.multi.infra.po.MongoUserPO;
import cn.structure.infra.sample.multi.infra.repository.delegate.UserRepositoryDelegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserMongoDelegate extends MongoRepositoryDelegate<UserEntity, MongoUserPO, Long> implements UserRepositoryDelegate {

    @Override
    public UserEntity findByName(String name) {
        UserEntity condition = new UserEntity();
        condition.setUsername(name);
        return queryOne(condition);
    }
}