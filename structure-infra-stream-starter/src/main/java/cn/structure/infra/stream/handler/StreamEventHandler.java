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

package cn.structure.infra.stream.handler;

/**
 * 事件处理器函数接口，由 {@code StreamEventManager} 在 {@code dispatch} 时回调。
 *
 * <p>设计意图：
 * <ul>
 *   <li>提供传统消息监听模型下的统一处理入口，与 {@code StreamEventRouter.StreamRouteHandler} 区分</li>
 *   <li>使用函数接口风格，便于以 Lambda 形式注册到 {@code StreamEventManager#registerListener}</li>
 *   <li>泛型 <code>T</code> 与监听器的 eventType 绑定，框架在派发前已完成类型过滤</li>
 * </ul>
 *
 * <p>协作关系：
 * <ul>
 *   <li>由 {@link cn.structure.infra.stream.manager.ListenerRegistration} 持有</li>
 *   <li>由 {@code EventListenerBeanPostProcessor} 在扫描 {@code @StreamEventListener} 方法时包装为 Lambda 实例</li>
 * </ul>
 *
 * @param <T> 事件负载类型
 */
public interface StreamEventHandler<T> {

    /**
     * 处理单个事件。
     *
     * @param event 事件负载，类型由监听器注册时声明的 eventType 决定
     */
    void handle(T event);

}
