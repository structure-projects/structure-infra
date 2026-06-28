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
