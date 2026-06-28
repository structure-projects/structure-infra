package cn.structure.infra.sample.infra.repository.delegate;

import cn.structure.infra.repository.RepositoryDelegate;
import cn.structure.infra.sample.infra.po.UserPO;

/**
 * <p>
 * 用户仓储代理
 * </p>
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
public interface UserRepositoryDelegate extends RepositoryDelegate<UserPO, Long> {

    UserPO finByName(String name);
}
