package cn.structure.infra.mongodb.configuration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.data.mongodb.core.MongoTemplate")
@ConditionalOnProperty(prefix = "structure.infra", name = "type", havingValue = "MONGODB", matchIfMissing = true)
@EnableMongoRepositories
public class MongoAutoConfiguration {
}
