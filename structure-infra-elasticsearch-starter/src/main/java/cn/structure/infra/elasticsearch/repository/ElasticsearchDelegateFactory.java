package cn.structure.infra.elasticsearch.repository;

import cn.structure.infra.repository.RepositoryDelegate;
import cn.structure.infra.repository.RepositoryDelegateFactory;
import cn.structure.infra.repository.RepositoryType;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

/**
 * Elasticsearch 仓储委托工厂
 * <p>
 * 自动创建 ElasticsearchRepositoryDelegate 实例
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
public class ElasticsearchDelegateFactory implements RepositoryDelegateFactory {

    private final ElasticsearchOperations elasticsearchOperations;

    public ElasticsearchDelegateFactory(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @Override
    public RepositoryType getType() {
        return RepositoryType.ELASTICSEARCH;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public RepositoryDelegate<?, ?> createDelegate(Class<?> poClass, Class<?> idClass) {
        try {
            return new ElasticsearchRepositoryDelegate(elasticsearchOperations, poClass);
        } catch (Exception e) {
            return null;
        }
    }
}
