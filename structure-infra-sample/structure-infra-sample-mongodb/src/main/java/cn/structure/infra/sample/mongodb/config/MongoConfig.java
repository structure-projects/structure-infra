package cn.structure.infra.sample.mongodb.config;

import com.mongodb.client.MongoClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@Configuration
public class MongoConfig {

    @Bean
    public SimpleMongoClientDatabaseFactory mongoDatabaseFactory() {
        String connectionString = "mongodb://user:123456@172.24.20.15:27017/test?authSource=admin&authMechanism=SCRAM-SHA-1";
        return new SimpleMongoClientDatabaseFactory(MongoClients.create(connectionString), "test");
    }

    @Bean
    public MongoTemplate mongoTemplate(SimpleMongoClientDatabaseFactory mongoDatabaseFactory) {
        return new MongoTemplate(mongoDatabaseFactory);
    }
}
