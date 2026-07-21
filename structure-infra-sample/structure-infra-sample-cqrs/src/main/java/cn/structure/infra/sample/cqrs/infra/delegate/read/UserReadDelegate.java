package cn.structure.infra.sample.cqrs.infra.delegate.read;

import cn.structure.infra.annotations.ReadDelegate;
import cn.structure.infra.elasticsearch.repository.ElasticsearchRepositoryDelegate;
import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.infra.po.MybatisUserPO;
import cn.structure.infra.sample.infra.repository.delegate.UserRepositoryDelegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ReadDelegate
public class UserReadDelegate extends ElasticsearchRepositoryDelegate<UserEntity, MybatisUserPO, Long> implements UserRepositoryDelegate {

    @Override
    public UserEntity findByName(String name) {
        return null;
    }
}
