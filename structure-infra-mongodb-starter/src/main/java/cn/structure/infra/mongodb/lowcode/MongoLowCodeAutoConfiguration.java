package cn.structure.infra.mongodb.lowcode;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * MongoDB 低代码自动配置类
 * <p>
 * 当低代码功能启用且存在 MongoTemplate 时，自动注册 MongoDB 低代码仓储工厂，
 * 使低代码路由引擎能够创建 MongoDB 类型的存储实例。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "structure.infra.lowcode", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MongoLowCodeAutoConfiguration {

    /**
     * 注册 MongoDB 低代码仓储工厂
     *
     * @param mongoTemplate MongoTemplate 实例
     * @return MongoDB 低代码仓储工厂实例
     */
    @Bean
    public MongoLowCodeRepoFactory mongoLowCodeRepoFactory(MongoTemplate mongoTemplate) {
        return new MongoLowCodeRepoFactory(mongoTemplate);
    }
}