package cn.structure.infra.sample.jpa.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA 测试配置
 * <p>
 * 配置 JPA 相关的组件扫描
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
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "cn.structure.infra.sample.infra.repository.mongodb.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "cn.structure.infra.sample.infra.repository.elasticsearch.*")
})
@EnableJpaRepositories(basePackages = "cn.structure.infra.sample.infra.repository.jpa")
public class JpaTestConfig {
}
