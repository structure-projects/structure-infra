package cn.structure.infra.sample.elasticsearch.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch 测试配置
 * <p>
 * 配置 Elasticsearch 相关的组件扫描
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Configuration
@ComponentScan(basePackages = {
        "cn.structure.infra.sample",
        "cn.structure.infra.repository"
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "cn.structure.infra.sample.infra.repository.mybatis.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "cn.structure.infra.sample.infra.repository.jpa.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "cn.structure.infra.sample.infra.repository.mongodb.*")
})
@EnableElasticsearchRepositories(basePackages = "cn.structure.infra.sample.infra.repository.elasticsearch")
public class ElasticsearchTestConfig {
}
