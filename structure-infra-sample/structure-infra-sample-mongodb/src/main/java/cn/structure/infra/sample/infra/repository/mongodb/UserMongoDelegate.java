package cn.structure.infra.sample.infra.repository.mongodb;

import cn.structure.infra.mongodb.repository.MongoRepositoryDelegate;
import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.infra.po.UserPO;
import cn.structure.infra.sample.infra.repository.delegate.UserRepositoryDelegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户仓储 MongoDB 委托实现
 * <p>
 * 负责 UserEntity 与 UserPO 之间的转换，
 * 内部使用 Spring Data MongoDB 进行持久化操作。
 * <p>
 * 注意：通过命名约定和泛型参数自动匹配对应的 RepositoryFacade，
 * 无需额外的 @DelegateFor 注解配置。
 *
 * @author chuck
 * @version 1.0.2
 * @since 2026/6/28
 */
@Slf4j
@Component
public class UserMongoDelegate extends MongoRepositoryDelegate<UserEntity, UserPO, Long> implements UserRepositoryDelegate {

    /**
     * 根据用户名查询用户
     * <p>
     * 使用 MongoTemplate 执行条件查询，将 PO 结果转换为 Entity 返回。
     *
     * @param name 用户名
     * @return 用户实体，不存在时返回 null
     */
    @Override
    public UserEntity findByName(String name) {
        UserEntity condition = new UserEntity();
        condition.setUsername(name);
        return queryOne(condition);
    }
}
