package cn.structure.infra.sample.mongodb.config;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration
@SpringBootApplication(excludeName = {
        "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration"
})
@ComponentScan(basePackages = {
        "cn.structure.infra.sample",
        "cn.structure.infra.repository"
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "cn.structure.infra.sample.infra.repository.mybatis.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "cn.structure.infra.sample.infra.repository.jpa.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "cn.structure.infra.sample.infra.repository.elasticsearch.*")
})
public class MongoTestConfig {
}
