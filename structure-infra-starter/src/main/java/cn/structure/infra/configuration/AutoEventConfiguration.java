package cn.structure.infra.configuration;

import cn.structure.infra.event.DefaultEventManagerImpl;
import cn.structure.infra.event.EventManager;
import cn.structure.infra.properties.InfraProperties;
import cn.structured.datascope.message.wrapper.DataScopeStreamBridge;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InfraProperties.class)
public class AutoEventConfiguration {

    @Bean
    @ConditionalOnBean(EventManager.class)
    public EventManager eventManager(ApplicationEventPublisher applicationEventPublisher,
                                     DataScopeStreamBridge streamBridge,
                                     InfraProperties infraProperties) {
        return new DefaultEventManagerImpl(applicationEventPublisher, streamBridge, infraProperties);
    }
}
