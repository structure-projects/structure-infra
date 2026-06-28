package cn.structure.infra.sample.infra.repository.elasticsearch;

import cn.structure.infra.annotations.DelegateFor;
import cn.structure.infra.elasticsearch.repository.ElasticsearchRepositoryDelegate;
import cn.structure.infra.repository.RepositoryType;
import cn.structure.infra.sample.infra.po.UserPO;
import cn.structure.infra.sample.infra.repository.delegate.UserRepositoryDelegate;
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
public class UserEsDelegate extends ElasticsearchRepositoryDelegate<UserPO, Long> implements UserRepositoryDelegate {

    public UserEsDelegate(ElasticsearchOperations elasticsearchOperations) {
        super(elasticsearchOperations, UserPO.class);
    }

    @Override
    public UserPO finByName(String name) {
        UserPO condition = new UserPO();
        condition.setUsername(name);
        return queryOne(condition);
    }

}
