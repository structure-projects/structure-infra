package cn.structure.infra.sample.infra.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存配置
 * <p>
 * 提供简单的内存缓存实现，用于数据权限缓存
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }
}
