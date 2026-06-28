package cn.structure.infra.elasticsearch.configuration;

import cn.structure.infra.elasticsearch.repository.ElasticsearchDelegateBeanPostProcessor;
import cn.structure.infra.elasticsearch.repository.ElasticsearchDelegateFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch 自动配置类
 * <p>
 * 当检测到 Elasticsearch 相关依赖时自动配置
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.data.elasticsearch.core.ElasticsearchOperations")
@ConditionalOnProperty(prefix = "structure.infra", name = "type", havingValue = "ELASTICSEARCH", matchIfMissing = true)
@EnableElasticsearchRepositories
public class ElasticsearchAutoConfiguration {

    @Bean
    @ConditionalOnBean(ElasticsearchOperations.class)
    public ElasticsearchDelegateFactory elasticsearchDelegateFactory(ElasticsearchOperations elasticsearchOperations) {
        return new ElasticsearchDelegateFactory(elasticsearchOperations);
    }

    @Bean
    @ConditionalOnBean(ElasticsearchOperations.class)
    public ElasticsearchDelegateBeanPostProcessor elasticsearchDelegateBeanPostProcessor() {
        return new ElasticsearchDelegateBeanPostProcessor();
    }
}
