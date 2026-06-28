package cn.structure.infra.sample.infra.repository;

import cn.structure.infra.repository.RepositoryFacade;
import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.domain.repository.UserRepository;
import cn.structure.infra.sample.infra.po.UserPO;
import cn.structure.infra.sample.infra.repository.delegate.UserRepositoryDelegate;

/**
 * 用户仓储基类
 * <p>
 * 提供通用的仓储实现，各存储技术模块可以继承此类并指定具体的存储类型
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
public abstract class AbstractUserRepositoryImpl extends RepositoryFacade<UserEntity, Long, UserPO, UserRepositoryDelegate> implements UserRepository {

    @Override
    public UserEntity findByName(String name) {
        UserPO po = this.baseDelegate.finByName(name);
        return this.toEntity(po);
    }
}
