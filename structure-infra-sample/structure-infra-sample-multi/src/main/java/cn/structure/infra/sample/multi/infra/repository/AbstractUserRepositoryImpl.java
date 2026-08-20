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

package cn.structure.infra.sample.multi.infra.repository;

import cn.structure.infra.repository.MultiRepositoryFacade;
import cn.structure.infra.sample.multi.domain.entity.UserEntity;
import cn.structure.infra.sample.multi.domain.repository.UserRepository;
import cn.structure.infra.sample.multi.infra.repository.delegate.UserRepositoryDelegate;

public abstract class AbstractUserRepositoryImpl extends MultiRepositoryFacade<UserEntity, Long, UserRepositoryDelegate> implements UserRepository {

    @Override
    public UserEntity findByName(String name) {
        return getCurrentDelegate().findByName(name);
    }
}