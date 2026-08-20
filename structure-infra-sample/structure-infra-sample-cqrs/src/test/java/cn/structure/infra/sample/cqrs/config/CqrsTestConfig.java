/*
Copyright 2023 Structure Projects

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	`http://www.apache.org/licenses/LICENSE-2.0`

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package cn.structure.infra.sample.cqrs.config;


import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
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
        "cn.structure.infra.sample.infra.po",
        "cn.structure.infra.sample.infra.mapper",
        "cn.structure.infra.repository",
        "cn.structure.infra.mybatis.plus",
        "cn.structure.infra.elasticsearch"
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "cn.structure.infra.sample.infra.repository.mybatis.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "cn.structure.infra.sample.infra.repository.jpa.*"),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = cn.structure.infra.sample.InfraSampleApplication.class)
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
}