package cn.structure.infra.sample.infra.repository.elasticsearch;

import cn.structure.infra.annotations.DelegateFor;
import cn.structure.infra.elasticsearch.repository.ElasticsearchRepositoryDelegate;
import cn.structure.infra.repository.RepositoryType;
import cn.structure.infra.sample.infra.po.UserPO;
import cn.structure.infra.sample.infra.repository.delegate.UserRepositoryDelegate;
import org.springframework.stereotype.Component;

/**
 * 用户仓储 Elasticsearch 实现
 * <p>
 * 使用 Spring Data Elasticsearch 实现用户数据的持久化操作
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Component
@DelegateFor(
        name = "userRepository",
        type = RepositoryType.ELASTICSEARCH,
        po = UserPO.class,
        description = "用户仓储 Elasticsearch 实现",
        priority = 10
)
public class UserEsDelegate extends ElasticsearchRepositoryDelegate<UserPO, Long> implements UserRepositoryDelegate {

    @Override
    public UserPO finByName(String name) {
        // TODO: 实现 Elasticsearch 查询逻辑
        return null;
    }

}
