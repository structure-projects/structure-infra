package cn.structure.infra.stream.configuration;

import cn.structure.infra.stream.manager.DefaultStreamEventManagerImpl;
import cn.structure.infra.stream.manager.StreamEventManager;
import cn.structure.infra.stream.properties.StreamProperties;
import cn.structure.infra.stream.processor.EventListenerBeanPostProcessor;
import cn.structure.infra.stream.processor.StreamBindingBeanFactoryPostProcessor;
import cn.structure.infra.stream.router.DefaultStreamEventRouterImpl;
import cn.structure.infra.stream.router.RouterProperties;
import cn.structure.infra.stream.router.StreamEventRouter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({StreamBridge.class})
@ConditionalOnProperty(prefix = "structure.infra.stream", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({StreamProperties.class, RouterProperties.class})
public class StreamAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StreamEventManager streamEventManager(StreamBridge streamBridge,
                                                  StreamProperties streamProperties) {
        return new DefaultStreamEventManagerImpl(streamBridge, streamProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public StreamEventRouter streamEventRouter() {
        return new DefaultStreamEventRouterImpl();
    }

    @Bean
    public static StreamBindingBeanFactoryPostProcessor streamBindingBeanFactoryPostProcessor() {
        return new StreamBindingBeanFactoryPostProcessor();
    }

    @Bean
    public EventListenerBeanPostProcessor eventListenerBeanPostProcessor(StreamEventManager streamEventManager,
                                                                          StreamProperties streamProperties) {
        return new EventListenerBeanPostProcessor(streamEventManager, streamProperties);
    }

}