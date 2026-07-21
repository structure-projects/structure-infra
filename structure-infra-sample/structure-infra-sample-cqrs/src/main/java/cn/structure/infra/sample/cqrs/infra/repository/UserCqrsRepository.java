package cn.structure.infra.sample.cqrs.infra.repository;

import cn.structure.infra.repository.CqrsRepositoryFacade;
import cn.structure.infra.sample.cqrs.infra.delegate.read.UserReadDelegate;
import cn.structure.infra.sample.cqrs.infra.delegate.write.UserWriteDelegate;
import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.domain.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * 用户 CQRS 仓储实现
 * <p>
 * 继承 CqrsRepositoryFacade，实现读写分离模式：
 * - 写操作通过 UserWriteDelegate（MyBatis Plus）执行
 * - 读操作通过 UserReadDelegate（Elasticsearch）执行
 * - 读操作失败时自动回退到写代理
 * <p>
 * 注意：通过泛型参数和 @ReadDelegate 注解自动匹配对应的代理实现，
 * 无需额外的 @Repository 注解配置。
 *
 * @author chuck
 * @version 1.0.2
 * @since 2026/6/28
 */
@Component("userCqrsRepository")
public class UserCqrsRepository extends CqrsRepositoryFacade<UserEntity, Long, UserWriteDelegate, UserReadDelegate> implements UserRepository {

    @Override
    public UserEntity findByName(String name) {
        return getReadDelegate().findByName(name);
    }
}