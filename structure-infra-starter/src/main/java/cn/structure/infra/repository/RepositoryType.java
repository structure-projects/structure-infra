package cn.structure.infra.repository;

/**
 * 仓储类型枚举
 * <p>
 * 标识底层持久化技术的种类，用于在 {@link RepositoryDelegateFactory} 创建 Delegate、
 * 以及在 {@link RepositoryBeanPostProcessor} 匹配 Delegate 时进行类型筛选。
 * <p>
 * {@link #AUTO} 表示由框架自动推断，匹配任意可用类型。
 *
 * @author chuck
 * @version 1.0.1
 * @since 2021/6/21 16:05
 */
public enum RepositoryType {

    /**
     * 原生 MyBatis
     */
    MYBATIS,

    /**
     * MyBatis-Plus 增强
     */
    MYBATIS_PLUS,

    /**
     * JPA / Hibernate
     */
    JPA,

    /**
     * 原生 JDBC
     */
    JDBC,

    /**
     * 通用 NoSQL（泛指）
     */
    NOSQL,

    /**
     * Redis 缓存数据库
     */
    REDIS,

    /**
     * MongoDB 文档数据库
     */
    MONGODB,

    /**
     * Elasticsearch 搜索引擎
     */
    ELASTICSEARCH,

    /**
     * 自动推断类型，匹配任意可用的 Delegate
     */
    AUTO
}
