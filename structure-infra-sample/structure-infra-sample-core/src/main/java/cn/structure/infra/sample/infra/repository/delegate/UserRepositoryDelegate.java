package cn.structure.infra.sample.infra.repository.delegate;

import cn.structure.infra.repository.RepositoryDelegate;
import cn.structure.infra.sample.infra.po.UserPO;

public interface UserRepositoryDelegate extends RepositoryDelegate<UserPO, Long> {

    UserPO finByName(String name);
}
