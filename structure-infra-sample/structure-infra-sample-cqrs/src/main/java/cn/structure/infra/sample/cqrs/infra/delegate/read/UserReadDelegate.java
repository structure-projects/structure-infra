package cn.structure.infra.sample.cqrs.infra.delegate.read;

import cn.structure.infra.annotations.DelegateFor;
import cn.structure.infra.elasticsearch.repository.ElasticsearchRepositoryDelegate;
import cn.structure.infra.repository.DelegateType;
import cn.structure.infra.repository.RepositoryType;
import cn.structure.infra.sample.infra.po.UserPO;
import cn.structure.infra.sample.infra.repository.delegate.UserRepositoryDelegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户读代理（READ）
 * <p>
 * 模拟 Elasticsearch 等读数据源，只实现读操作，不实现写操作。
 * <p>
 * 特点：
 * - 只实现 IQueryDelegate 接口（只读能力）
 * - 不实现 RepositoryDelegate 接口（无写操作）
 * - 可以与写代理是完全不同的类型
 * - 如果读代理执行失败，自动回退到写代理（baseDelegate）
 * <p>
 * delegateType = READ 表示这是读代理
 */
@Slf4j
@Component
@DelegateFor(
        name = "userCqrsRepository",
        po = UserPO.class,
        delegateType = DelegateType.READ,
        type = RepositoryType.ELASTICSEARCH

)
public class UserReadDelegate extends ElasticsearchRepositoryDelegate<UserPO, Long> implements UserRepositoryDelegate {

    @Override
    public UserPO finByName(String name) {
        return null;
    }
}