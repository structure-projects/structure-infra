package cn.structure.infra.sample.mongodb.lowcode.config;

import cn.structure.infra.lowcode.configuration.LowCodeAutoConfiguration;
import cn.structure.infra.mongodb.lowcode.MongoLowCodeAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * MongoDB 低代码测试配置
 * <p>
 * 导入低代码仓储的自动配置，配合 MongoTestConfig 和 MockMongoConfiguration 使用。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@Configuration
@Import({
        LowCodeAutoConfiguration.class,
        MongoLowCodeAutoConfiguration.class
})
public class MongoLowCodeTestConfig {
}
