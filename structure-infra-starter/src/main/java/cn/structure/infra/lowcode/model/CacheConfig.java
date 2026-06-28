package cn.structure.infra.lowcode.model;

import lombok.Data;

import java.util.concurrent.TimeUnit;

/**
 * 缓存配置
 * <p>
 * 定义低代码仓储的缓存策略，启用后查询数据会自动缓存以提升性能。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@Data
public class CacheConfig {

    /**
     * 是否启用缓存
     */
    private boolean enabled;

    /**
     * 缓存过期时间
     */
    private long ttl = 300;

    /**
     * 时间单位
     */
    private TimeUnit timeUnit = TimeUnit.SECONDS;
}
