package cn.structure.infra.sample.stream.config;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@SpringBootApplication(excludeName = {
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration"
})
@ComponentScan(basePackages = "cn.structure.infra.sample.stream")
public class StreamTestConfig {
}
