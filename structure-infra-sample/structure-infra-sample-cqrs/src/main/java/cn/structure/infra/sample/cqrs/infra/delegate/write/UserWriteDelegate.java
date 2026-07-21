package cn.structure.infra.sample.cqrs.infra.delegate.write;

import cn.structure.infra.mybatis.plus.repository.MybatisPlusRepositoryDelegate;
import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.infra.po.MybatisUserPO;
import cn.structure.infra.sample.infra.repository.delegate.UserRepositoryDelegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
 */
@Slf4j
@Component
public class UserWriteDelegate extends MybatisPlusRepositoryDelegate<UserEntity, MybatisUserPO, Long> implements UserRepositoryDelegate {

    @Override
    public UserEntity findByName(String name) {
        return null;
    }
}
