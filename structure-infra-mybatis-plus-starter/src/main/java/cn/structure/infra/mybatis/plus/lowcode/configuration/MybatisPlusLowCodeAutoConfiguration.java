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

package cn.structure.infra.mybatis.plus.lowcode.configuration;

import cn.structure.infra.mybatis.plus.lowcode.MySqlLowCodeRepoFactory;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis Plus 低代码自动配置类
 * <p>
 * 当低代码功能启用时，自动注册 MySQL 低代码仓储工厂，
 * 使低代码路由引擎能够创建 MySQL 类型的存储实例。
 * <p>
 * 基于 MyBatis SqlSession 实现，SQL 拦截器支持说明：
 * <ul>
 *   <li>所有 CRUD SQL 通过动态注册 MappedStatement 的方式执行</li>
 *   <li>SQL 会经过 MyBatis 拦截器链，支持分页插件、数据权限、SQL 监控等</li>
 *   <li>明确列名查询：所有 SELECT 语句使用明确列名替代 SELECT *</li>
 *   <li>多方言适配：支持 MySQL/H2/Oracle/PostgreSQL/SQL Server 自动适配</li>
 * </ul>
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "structure.infra.lowcode", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MybatisPlusLowCodeAutoConfiguration {

    /**
     * 注册 MySQL 低代码仓储工厂
     *
     * @param sqlSessionFactory MyBatis SqlSessionFactory
     * @return MySQL 低代码仓储工厂实例
     */
    @Bean
    public MySqlLowCodeRepoFactory mySqlLowCodeRepoFactory(SqlSessionFactory sqlSessionFactory) {
        return new MySqlLowCodeRepoFactory(sqlSessionFactory);
    }
}