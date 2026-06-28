package cn.structure.infra.mongodb.configuration;

import cn.structure.infra.mongodb.repository.MongoDelegateBeanPostProcessor;
import cn.structure.infra.mongodb.repository.MongoDelegateFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB 自动配置类
 * <p>
 * 当检测到 MongoDB 相关依赖时自动配置
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.data.mongodb.core.MongoTemplate")
@ConditionalOnProperty(prefix = "structure.infra", name = "type", havingValue = "MONGODB", matchIfMissing = true)
@EnableMongoRepositories
public class MongoAutoConfiguration {

    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    public MongoDelegateFactory mongoDelegateFactory(MongoTemplate mongoTemplate) {
        return new MongoDelegateFactory(mongoTemplate);
    }

    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    public MongoDelegateBeanPostProcessor mongoDelegateBeanPostProcessor() {
        return new MongoDelegateBeanPostProcessor();
    }
}
