package cn.structure.infra.sample.infra.repository.elasticsearch;

import cn.structure.infra.annotations.DelegateFor;
import cn.structure.infra.elasticsearch.repository.ElasticsearchRepositoryDelegate;
import cn.structure.infra.repository.RepositoryType;
import cn.structure.infra.sample.infra.po.UserPO;
import cn.structure.infra.sample.infra.repository.delegate.UserRepositoryDelegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

@Component
@DelegateFor(
        name = "userRepository",
        type = RepositoryType.ELASTICSEARCH,
        po = UserPO.class,
        description = "用户仓储 Elasticsearch 实现",
        priority = 10
)
@Slf4j
public class UserEsDelegate extends ElasticsearchRepositoryDelegate<UserPO, Long> implements UserRepositoryDelegate {

    @Override
    public UserPO finByName(String name) {
        log.info("使用 Elasticsearch 实现 finByName");
        UserPO condition = new UserPO();
        condition.setUsername(name);
        return queryOne(condition);
    }

}
