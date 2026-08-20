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

package cn.structure.infra.event;

import cn.structure.infra.properties.InfraProperties;
import cn.structured.datascope.message.wrapper.DataScopeStreamBridge;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 默认事件管理器实现
 * <p>
 * {@link EventManager} 的内置实现，负责根据 {@link Event#getEventChannel()}
 * 将事件路由到对应的发布渠道：
 * <ul>
 *   <li>{@link EventChannel#SPRING_EVENT} —— 通过 {@link ApplicationEventPublisher} 发布 Spring 应用事件，仅限本 JVM</li>
 *   <li>{@link EventChannel#MESSAGE_EVENT} —— 通过 {@link DataScopeStreamBridge} 发送到消息中间件，可跨服务</li>
 *   <li>{@link EventChannel#DEFAULT} —— 委托给 {@link InfraProperties#getDefaultEventChannel()} 全局配置决定</li>
 * </ul>
 * <p>
 * 该 Bean 由 {@link cn.structure.infra.configuration.AutoEventConfiguration} 自动注册，
 * 仅在容器中已存在 {@link EventManager} 类型 Bean 时生效。
 *
 * @author chuck
 * @version 1.0.1
 * @since 2021/6/21 16:05
 */
@AllArgsConstructor
public class DefaultEventManagerImpl implements EventManager {

    /**
     * Spring 应用事件发布器，用于 {@link EventChannel#SPRING_EVENT} 渠道
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 数据权限消息桥接器，用于 {@link EventChannel#MESSAGE_EVENT} 渠道
     */
    private final DataScopeStreamBridge streamBridge;

    /**
     * 框架配置属性，提供 {@link EventChannel#DEFAULT} 渠道的实际路由策略
     */
    private final InfraProperties infraProperties;

    /**
     * 发布事件
     * <p>
     * 路由逻辑：
     * <ol>
     *   <li>若事件渠道为 {@link EventChannel#DEFAULT}，按 {@link InfraProperties#getDefaultEventChannel()} 配置选择发布方式</li>
     *   <li>否则按事件自身声明的渠道发布</li>
     * </ol>
     *
     * @param event 待发布事件
     */
    @Override
    public void publish(Event event) {
        // DEFAULT 渠道：根据全局配置决定实际发布方式
        if (event.getEventChannel().equals(EventChannel.DEFAULT)) {
            if (infraProperties.getDefaultEventChannel() == EventChannel.SPRING_EVENT) {
                eventPublisher.publishEvent(event);
            }
            if (infraProperties.getDefaultEventChannel() == EventChannel.MESSAGE_EVENT) {
                streamBridge.send(event.getEventId(), event);
            }
        } else {
            // 非 DEFAULT 渠道：按事件自身声明的渠道发布
            if (event.getEventChannel() == EventChannel.SPRING_EVENT) {
                eventPublisher.publishEvent(event);
            }
            if (event.getEventChannel() == EventChannel.MESSAGE_EVENT) {
                streamBridge.send(event.getEventId(), event);
            }
        }
    }
}
