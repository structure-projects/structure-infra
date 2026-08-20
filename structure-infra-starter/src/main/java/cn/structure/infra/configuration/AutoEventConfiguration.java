/*
Copyright 2023 Structure Projects

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	`http://www.apache.org/licenses/LICENSE-2.0`

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

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

/**
 * 事件子系统自动装配配置类
 * <p>
 * 负责注册 {@link DefaultEventManagerImpl} 作为 {@link EventManager} 的默认实现，
 * 将事件发布能力接入框架。
 * <p>
 * 装配条件：仅当容器中已存在 {@link EventManager} 类型 Bean（通常由使用方主动声明）
 * 时才会注册默认实现，避免重复注册。
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Configuration
@EnableConfigurationProperties(InfraProperties.class)
public class AutoEventConfiguration {

    /**
     * 注册默认事件管理器
     * <p>
     * 注入 Spring 事件发布器、消息桥接器和框架配置，使事件可按 {@link cn.structure.infra.event.EventChannel}
     * 进行路由发布。
     *
     * @param applicationEventPublisher Spring 应用事件发布器
     * @param streamBridge              数据权限消息桥接器
     * @param infraProperties           框架配置属性
     * @return 默认事件管理器实现
     */
    @Bean
    @ConditionalOnBean(EventManager.class)
    public EventManager eventManager(ApplicationEventPublisher applicationEventPublisher,
                                     DataScopeStreamBridge streamBridge,
                                     InfraProperties infraProperties) {
        return new DefaultEventManagerImpl(applicationEventPublisher, streamBridge, infraProperties);
    }
}
