package cn.structure.infra.sample.infra.repository;

import cn.structure.infra.annotations.Repository;
import cn.structure.infra.repository.RepositoryType;
import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.infra.po.UserPO;
import org.springframework.stereotype.Component;

@Repository(value = "用户仓储", type = RepositoryType.MYBATIS_PLUS, entity = UserEntity.class, po = UserPO.class)
@Component("userRepository")
public class UserRepositoryImpl extends AbstractUserRepositoryImpl {
}