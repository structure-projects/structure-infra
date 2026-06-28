package cn.structure.infra.sample.infra.repository.jpa;

import cn.structure.infra.annotations.DelegateFor;
import cn.structure.infra.jpa.repository.JpaRepositoryDelegate;
import cn.structure.infra.repository.RepositoryType;
import cn.structure.infra.sample.infra.po.UserPO;
import cn.structure.infra.sample.infra.repository.delegate.UserRepositoryDelegate;
import org.springframework.stereotype.Component;

/**
 * 用户仓储 JPA 实现
 * <p>
 * 使用 Spring Data JPA 实现用户数据的持久化操作
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Component
@DelegateFor(
        name = "userRepository",
        type = RepositoryType.JPA,
        po = UserPO.class,
        description = "用户仓储 JPA 实现",
        priority = 10
)
public class UserJpaDelegate extends JpaRepositoryDelegate<UserPO, Long> implements UserRepositoryDelegate {

    @Override
    public UserPO finByName(String name) {
        // TODO: 实现 JPA 查询逻辑
        return null;
    }

}
