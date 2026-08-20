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

import lombok.Getter;

/**
 * 事件渠道类型枚举
 * <p>
 * 定义事件发布的目标渠道，{@link EventManager} 根据事件声明的渠道类型
 * 选择对应的发布方式（Spring 应用事件 / 消息中间件）。
 * <p>
 * 当事件使用 {@link #DEFAULT} 时，实际渠道由 {@link cn.structure.infra.properties.InfraProperties#getDefaultEventChannel()}
 * 全局配置决定。
 *
 * @author chuck
 * @version 1.0.1
 * @since 2021/6/21 16:05
 */
@Getter
public enum EventChannel {
    /**
     * 默认渠道：由全局配置 {@code structure.infra.default-event-channel} 决定实际发布方式
     */
    DEFAULT,
    /**
     * Spring 应用事件渠道：通过 {@link org.springframework.context.ApplicationEventPublisher} 发布，仅在本 JVM 内传播
     */
    SPRING_EVENT,
    /**
     * 消息事件渠道：通过 {@link cn.structured.datascope.message.wrapper.DataScopeStreamBridge} 发送到消息中间件，可跨服务传播
     */
    MESSAGE_EVENT,
    ;
}
