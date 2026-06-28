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

    @Override
    public StorageType getType() {
        return StorageType.MYSQL;
    }

    @Override
    public LowCodeStorage createStorage(ResourceSchema schema, RepositoryConfig config) {
        return new MySqlLowCodeStorage(schema, sqlSessionFactory);
    }
}