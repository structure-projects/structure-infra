package cn.structure.infra.sample.infra.repository;

import cn.structure.infra.annotations.Repository;
import cn.structure.infra.repository.RepositoryType;
import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.infra.po.UserPO;
import org.springframework.stereotype.Component;

/**
 * 用户仓储 JPA 实现
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Repository(value = "用户仓储", type = RepositoryType.JPA, entity = UserEntity.class, po = UserPO.class)
@Component("userRepository")
public class UserJpaRepositoryImpl extends AbstractUserRepositoryImpl {
}
