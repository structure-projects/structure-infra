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

package cn.structure.infra.mybatis.plus.lowcode;

import cn.structure.infra.lowcode.model.RepositoryConfig;
import cn.structure.infra.lowcode.model.ResourceSchema;
import cn.structure.infra.lowcode.model.StorageType;
import cn.structure.infra.lowcode.repository.LowCodeRepoFactory;
import cn.structure.infra.lowcode.repository.LowCodeStorage;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * MySQL 低代码仓储工厂
 * <p>
 * 负责创建 MySQL 类型的低代码存储实例，内部使用 MyBatis SqlSession 执行动态 SQL。
 * <p>
 * 核心特性：
 * <ul>
 *   <li>拦截器支持：通过动态注册 MappedStatement，SQL 经过 MyBatis 拦截器链</li>
 *   <li>多方言适配：支持 MySQL、H2、Oracle、PostgreSQL、SQL Server 自动检测</li>
 *   <li>性能优化：明确列名查询替代 SELECT *，方言适配分页语法</li>
 * </ul>
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
public class MySqlLowCodeRepoFactory implements LowCodeRepoFactory {

    private final SqlSessionFactory sqlSessionFactory;

    /**
     * 通过 SqlSessionFactory 构造
     *
     * @param sqlSessionFactory MyBatis SqlSessionFactory
     */
    public MySqlLowCodeRepoFactory(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    /**
     * 返回该工厂支持的存储类型，用于低代码路由引擎匹配。
     *
     * @return 固定返回 {@link StorageType#MYSQL}
     */
    @Override
    public StorageType getType() {
        return StorageType.MYSQL;
    }

    /**
     * 创建 MySQL 低代码存储实例。
     * <p>
     * 内部构造 {@link MySqlLowCodeStorage}，由其在初始化时自动检测数据库方言并完成建表。
     *
     * @param schema 资源 schema 定义（表名、字段、主键、索引等）
     * @param config 仓储配置（当前实现未使用，保留以匹配 SPI 签名）
     * @return 低代码存储实例
     */
    @Override
    public LowCodeStorage createStorage(ResourceSchema schema, RepositoryConfig config) {
        return new MySqlLowCodeStorage(schema, sqlSessionFactory);
    }
}