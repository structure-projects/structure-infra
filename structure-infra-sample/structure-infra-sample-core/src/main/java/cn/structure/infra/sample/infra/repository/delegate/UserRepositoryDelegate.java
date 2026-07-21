package cn.structure.infra.sample.infra.repository.delegate;

import cn.structure.infra.repository.RepositoryDelegate;
import cn.structure.infra.sample.domain.entity.UserEntity;

/**
 * 用户仓储代理
 * <p>
 * 定义用户仓储的委托接口，面向领域实体（UserEntity）。
 * 具体实现负责内部的 Entity ↔ PO 转换。
 *
 * @author chuck
 * @version 1.0.2
 * @since 2026/6/28
 */
public interface UserRepositoryDelegate extends RepositoryDelegate<UserEntity, Long> {

    UserEntity findByName(String name);
}
