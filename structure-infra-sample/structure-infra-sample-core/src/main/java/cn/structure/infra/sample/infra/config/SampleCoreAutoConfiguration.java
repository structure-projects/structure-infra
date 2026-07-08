package cn.structure.infra.sample.infra.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableCaching
public class SampleCoreAutoConfiguration {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }

}