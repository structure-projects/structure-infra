package cn.structure.infra.sample.multi.domain.repository;

import cn.structure.common.repository.ICrudRepository;
import cn.structure.infra.sample.multi.domain.entity.UserEntity;

public interface UserRepository extends ICrudRepository<UserEntity, Long> {

    UserEntity findByName(String name);
}