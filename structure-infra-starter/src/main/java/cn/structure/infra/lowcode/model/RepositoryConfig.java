package cn.structure.infra.lowcode.model;

import lombok.Data;

/**
 * 仓储配置
 * <p>
 * 定义低代码资源的存储配置，包括存储类型、数据源、CQRS 读写分离、缓存等。
 * 路由引擎根据此配置选择并创建对应的仓储实现。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@Data
public class RepositoryConfig {

    /**
     * 存储类型（默认 MySQL）
     */
    private StorageType type = StorageType.MYSQL;

    /**
     * 数据源名称
     */
    private String datasource;

    /**
     * CQRS 读写分离配置
     */
    private CqrsConfig cqrs;

    /**
     * 缓存配置
     */
    private CacheConfig cache;

    /**
     * 是否启用了 CQRS 读写分离
     *
     * @return true 表示启用
     */
    public boolean isCqrsEnabled() {
        return cqrs != null && cqrs.isEnabled();
    }

    /**
     * 是否启用了缓存
     *
     * @return true 表示启用
     */
    public boolean isCacheEnabled() {
        return cache != null && cache.isEnabled();
    }
}
