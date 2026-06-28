package cn.structure.infra.sample.cqrs.infra.repositoory;

import cn.structure.infra.annotations.Repository;
import cn.structure.infra.repository.InMemoryRepositoryDelegate;
import cn.structure.infra.repository.RepositoryFacade;
import cn.structure.infra.sample.cqrs.infra.delegate.read.UserReadDelegate;
import cn.structure.infra.sample.cqrs.infra.delegate.write.UserWriteDelegate;
import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.domain.repository.UserRepository;
import cn.structure.infra.sample.infra.po.UserPO;
import org.springframework.stereotype.Component;

/**
 * 用户 CQRS 仓储
 * <p>
 * 启用了 CQRS 读写分离模式：
 * - 写操作：通过 baseDelegate（写代理）执行
 * - 读操作：通过 readDelegate（读代理）执行，失败自动回退到 baseDelegate
 * <p>
 * 使用条件（必须同时满足）：
 * 1. cqrs = true
 * 2. readDelegateClass 指定了读代理类
 */
@Repository(
        entity = UserEntity.class,
        po = UserPO.class,
        cqrs = true,
        readDelegateClass = UserReadDelegate.class
)
@Component("userCqrsRepository")
public class UserCqrsRepository extends RepositoryFacade<UserEntity, Long, UserPO, UserWriteDelegate> implements UserRepository {
    @Override
    public UserEntity findByName(String name) {
        return null;
    }
}