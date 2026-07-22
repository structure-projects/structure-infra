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