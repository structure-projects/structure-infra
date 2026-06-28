package cn.structure.infra.sample.elasticsearch.config;

import cn.structure.infra.lowcode.configuration.LowCodeAutoConfiguration;
import cn.structure.infra.elasticsearch.lowcode.ElasticsearchLowCodeAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Elasticsearch 低代码测试配置
 * <p>
 * 用于测试 Elasticsearch 低代码仓储功能，导入低代码相关配置。
 */
@SpringBootApplication
@Import({
        LowCodeAutoConfiguration.class,
        ElasticsearchLowCodeAutoConfiguration.class
})
public class ElasticsearchLowCodeTestConfig {
}
