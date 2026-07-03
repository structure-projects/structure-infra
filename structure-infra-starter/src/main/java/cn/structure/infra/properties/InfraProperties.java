package cn.structure.infra.properties;

import cn.structure.infra.event.EventChannel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Data
@Configuration
@ConfigurationProperties(prefix = "structure.infra")
public class InfraProperties {

    /**
     * 默认事件类型
     */
    private EventChannel defaultEventChannel = EventChannel.SPRING_EVENT;

    /**
     * 是否开启CQRS
     */
    private Boolean cqrs = false;

    /**
     * 缓存时间
     *
     * @return
     */
    private Long cacheTime = 60L;

    /**
     * 缓存时间单位
     *
     * @return
     */
    private TimeUnit cacheTimeUnit = TimeUnit.SECONDS;

    /**
     * 调度线程池大小，默认 CPU 核心数
     */
    private Integer schedulePoolSize = Runtime.getRuntime().availableProcessors();
}
