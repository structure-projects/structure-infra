package cn.structure.infra.elasticsearch.configuration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.data.elasticsearch.core.ElasticsearchOperations")
@ConditionalOnProperty(prefix = "structure.infra", name = "type", havingValue = "ELASTICSEARCH", matchIfMissing = true)
@EnableElasticsearchRepositories
public class ElasticsearchAutoConfiguration {
}
