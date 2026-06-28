package cn.structure.infra.sample.cqrs.config;

import cn.structure.infra.elasticsearch.repository.ElasticsearchDelegateBeanPostProcessor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * CQRS 测试配置
 * <p>
 * 同时加载多种目标代理，用于测试读写分离模式：
 * <ul>
 *   <li>BASE 代理（MyBatis Plus）：负责写操作</li>
 *   <li>READ 代理（Elasticsearch）：负责读操作</li>
 * </ul>
 * <p>
 * 排除不使用的自动配置：
 * <ul>
 *   <li>JPA 相关配置</li>
 *   <li>MongoDB 相关配置</li>
 * </ul>
 * <p>
 * 启用的配置：
 * <ul>
 *   <li>MyBatis Plus（用于 BASE 写代理）</li>
 *   <li>Elasticsearch（用于 READ 读代理，通过 Mock 实现）</li>
 * </ul>
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Configuration
@SpringBootApplication(excludeName = {
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
})
@ComponentScan(basePackages = {
        "cn.structure.infra.sample.cqrs",
        "cn.structure.infra.sample.infra",
        "cn.structure.infra.repository",
        "cn.structure.infra.mybatis.plus",
        "cn.structure.infra.elasticsearch"
})
@MapperScan("cn.structure.infra.sample.infra.mapper")
@Import({
        cn.structure.infra.mybatis.plus.configuration.MybatisPlusAutoConfiguration.class,
        cn.structure.infra.elasticsearch.configuration.ElasticsearchAutoConfiguration.class,
        MockElasticsearchConfiguration.class
})
public class CqrsTestConfig {

    /**
     * MyBatis Plus 分页插件
     * <p>
     * 用于支持 MyBatis Plus 的分页查询功能
     *
     * @return MybatisPlusInterceptor 实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        return interceptor;
    }

    /**
     * Elasticsearch 委托 Bean 后处理器
     * <p>
     * 显式注册，确保 Mock 环境下 ElasticsearchOperations 能正确注入到 Delegate 中。
     * 由于测试环境下 @ConditionalOnBean 可能因注册顺序问题不满足，因此手动注册。
     *
     * @return ElasticsearchDelegateBeanPostProcessor 实例
     */
    @Bean
    public ElasticsearchDelegateBeanPostProcessor elasticsearchDelegateBeanPostProcessor() {
        return new ElasticsearchDelegateBeanPostProcessor();
    }
}