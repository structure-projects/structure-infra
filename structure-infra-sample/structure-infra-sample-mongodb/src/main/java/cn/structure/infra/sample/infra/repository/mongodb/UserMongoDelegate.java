package cn.structure.infra.sample.infra.repository.mongodb;

import cn.structure.infra.annotations.DelegateFor;
import cn.structure.infra.mongodb.repository.MongoRepositoryDelegate;
import cn.structure.infra.repository.RepositoryType;
import cn.structure.infra.sample.infra.po.UserPO;
import cn.structure.infra.sample.infra.repository.delegate.UserRepositoryDelegate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
@DelegateFor(
        name = "userRepository",
        type = RepositoryType.MONGODB,
        po = UserPO.class,
        description = "用户仓储 MongoDB 实现",
        priority = 10
)
public class UserMongoDelegate extends MongoRepositoryDelegate<UserPO, Long> implements UserRepositoryDelegate {

    public UserMongoDelegate(MongoTemplate mongoTemplate) {
        super(mongoTemplate, UserPO.class);
    }

    @Override
    public UserPO finByName(String name) {
        UserPO condition = new UserPO();
        condition.setUsername(name);
        return queryOne(condition);
    }
}
