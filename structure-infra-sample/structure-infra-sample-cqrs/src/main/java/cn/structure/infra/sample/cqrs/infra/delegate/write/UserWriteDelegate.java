package cn.structure.infra.sample.cqrs.infra.delegate.write;

import cn.structure.infra.annotations.DelegateFor;
import cn.structure.infra.mybatis.plus.repository.MybatisPlusRepositoryDelegate;
import cn.structure.infra.repository.DelegateType;
import cn.structure.infra.repository.InMemoryRepositoryDelegate;
import cn.structure.infra.repository.RepositoryType;
import cn.structure.infra.sample.infra.po.UserPO;
import cn.structure.infra.sample.infra.repository.delegate.UserRepositoryDelegate;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户写代理（BASE）
 * <p>
 * 模拟数据库写操作，负责处理所有写操作：
 * - save
 * - removeById
 * - saveBatch
 * - removeBatchByIds
 * <p>
 * 同时也可以处理读操作（作为读操作的兜底）
 * <p>
 * delegateType = BASE 表示这是基础/写代理
 */
@Slf4j
@DelegateFor(
        name = "userCqrsRepository",
        po = UserPO.class,
        delegateType = DelegateType.BASE,
        type = RepositoryType.MYBATIS_PLUS
)
public class UserWriteDelegate  extends MybatisPlusRepositoryDelegate<UserPO, Long> implements UserRepositoryDelegate {

    @Override
    public UserPO finByName(String name) {
        return null;
    }
}