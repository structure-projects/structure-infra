package cn.structure.infra.sample.multi.infra.repository.delegate;

import cn.structure.infra.repository.RepositoryDelegate;
import cn.structure.infra.sample.multi.domain.entity.UserEntity;

public interface UserRepositoryDelegate extends RepositoryDelegate<UserEntity, Long> {

    UserEntity findByName(String name);
}