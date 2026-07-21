package cn.structure.infra.sample.multi.infra.repository.mongodb;

import cn.structure.infra.mongodb.repository.MongoRepositoryDelegate;
import cn.structure.infra.sample.multi.domain.entity.UserEntity;
import cn.structure.infra.sample.multi.infra.po.MongoUserPO;
import cn.structure.infra.sample.multi.infra.repository.delegate.UserRepositoryDelegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserMongoDelegate extends MongoRepositoryDelegate<UserEntity, MongoUserPO, Long> implements UserRepositoryDelegate {

    @Override
    public UserEntity findByName(String name) {
        UserEntity condition = new UserEntity();
        condition.setUsername(name);
        return queryOne(condition);
    }
}