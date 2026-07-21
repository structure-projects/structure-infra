package cn.structure.infra.sample.infra.repository;

import cn.structure.infra.repository.RepositoryFacade;
import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.domain.repository.UserRepository;
import cn.structure.infra.sample.infra.repository.delegate.UserRepositoryDelegate;

/**
 * 用户仓储基类
 * <p>
 * 提供通用的仓储实现，各存储技术模块可以继承此类并指定具体的存储类型。
 * <p>
 * 注意：PO 类型已从 Facade 层移除，Entity ↔ PO 转换由具体的 Delegate 实现负责。
 *
 * @author chuck
 * @version 1.0.2
 * @since 2026/6/28
 */
public abstract class AbstractUserRepositoryImpl extends RepositoryFacade<UserEntity, Long> implements UserRepository {

    @Override
    public UserEntity findByName(String name) {
        return ((UserRepositoryDelegate) getDelegate()).findByName(name);
    }
}
