package cn.structure.infra.sample.infra.repository.jpa;

import cn.structure.infra.annotations.DelegateFor;
import cn.structure.infra.jpa.repository.JpaRepositoryDelegate;
import cn.structure.infra.repository.RepositoryType;
import cn.structure.infra.sample.infra.po.UserPO;
import cn.structure.infra.sample.infra.repository.delegate.UserRepositoryDelegate;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
@DelegateFor(
        name = "userRepository",
        type = RepositoryType.JPA,
        po = UserPO.class,
        description = "用户仓储 JPA 实现",
        priority = 10
)
public class UserJpaDelegate extends JpaRepositoryDelegate<UserPO, Long> implements UserRepositoryDelegate {

    public UserJpaDelegate(EntityManager entityManager) {
        super(entityManager, UserPO.class);
    }

    @Override
    public UserPO finByName(String name) {
        UserPO condition = new UserPO();
        condition.setUsername(name);
        return queryOne(condition);
    }

}
