package cn.structure.infra.lowcode.model;

/**
 * 存储类型枚举
 * <p>
 * 定义低代码仓储支持的存储引擎类型，用于路由到对应的仓储实现。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
public enum StorageType {

    /**
     * MySQL 关系型数据库
     */
    MYSQL,

    /**
     * MongoDB 文档数据库
     */
    MONGODB,

    /**
     * Elasticsearch 搜索引擎
     */
    ELASTICSEARCH,

    /**
     * Redis 缓存数据库
     */
    REDIS,

    /**
     * 内存存储（用于测试或临时数据）
     */
    IN_MEMORY
}
