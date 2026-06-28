package cn.structure.infra.elasticsearch.lowcode;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

/**
 * Elasticsearch 低代码自动配置类
 * <p>
 * 当低代码功能启用且存在 ElasticsearchOperations 时，自动注册 Elasticsearch 低代码仓储工厂，
 * 使低代码路由引擎能够创建 Elasticsearch 类型的存储实例。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "structure.infra.lowcode", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchLowCodeAutoConfiguration {

    /**
     * 注册 Elasticsearch 低代码仓储工厂
     *
     * @param elasticsearchOperations ElasticsearchOperations 实例
     * @return Elasticsearch 低代码仓储工厂实例
     */
    @Bean
    public ElasticsearchLowCodeRepoFactory elasticsearchLowCodeRepoFactory(ElasticsearchOperations elasticsearchOperations) {
        return new ElasticsearchLowCodeRepoFactory(elasticsearchOperations);
    }
}