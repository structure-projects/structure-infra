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

package cn.structure.infra.stream.manager;

import cn.structure.infra.stream.handler.StreamEventHandler;

/**
 * 监听器注册信息，承载单个 {@link StreamEventHandler} 在 {@link StreamEventManager} 中的全部上下文。
 *
 * <p>设计意图：
 * <ul>
 *   <li>将监听器与其元数据（eventType/condition/destination/group/listenerId）打包为一等公民对象，
 *       便于 {@link DefaultStreamEventManagerImpl#dispatch} 时统一迭代过滤</li>
 *   <li>支持 Builder 模式构建，避免长参数构造方法的可读性问题</li>
 *   <li>listenerId 由 UUID 生成，作为精确注销的句柄</li>
 * </ul>
 *
 * <p>协作关系：
 * <ul>
 *   <li>由 {@link DefaultStreamEventManagerImpl#registerListener} 创建并加入注册表</li>
 *   <li>由 {@link DefaultStreamEventManagerImpl#dispatch} 在派发时读取 eventType 与 condition 进行过滤</li>
 * </ul>
 *
 * @param <T> 事件负载类型
 */
public class ListenerRegistration<T> {

    /**
     * 监听器唯一 ID，注册时由 UUID 生成，用于精确注销。
     */
    private String listenerId;
    /**
     * 事件负载类型，dispatch 时据此进行类型过滤（isInstance 判断）。
     */
    private Class<T> eventType;
    /**
     * 事件处理器回调。
     */
    private StreamEventHandler<T> handler;
    /**
     * SpEL 条件表达式，可通过 {@code #event} 引用事件，留空表示无条件。
     */
    private String condition;
    /**
     * 目标 destination（exchange/topic）。
     */
    private String destination;
    /**
     * 消费者组名。
     */
    private String group;

    /**
     * 默认构造方法，供反序列化或 Builder 使用。
     */
    public ListenerRegistration() {
    }

    /**
     * 全参构造方法。
     *
     * @param listenerId  监听器唯一 ID
     * @param eventType   事件负载类型
     * @param handler     事件处理器
     * @param condition   SpEL 条件表达式
     * @param destination 目标 destination
     * @param group       消费者组
     */
    public ListenerRegistration(String listenerId, Class<T> eventType, StreamEventHandler<T> handler,
                                String condition, String destination, String group) {
        this.listenerId = listenerId;
        this.eventType = eventType;
        this.handler = handler;
        this.condition = condition;
        this.destination = destination;
        this.group = group;
    }

    /**
     * @return 监听器唯一 ID
     */
    public String getListenerId() {
        return listenerId;
    }

    /**
     * @param listenerId 监听器唯一 ID
     */
    public void setListenerId(String listenerId) {
        this.listenerId = listenerId;
    }

    /**
     * @return 事件负载类型
     */
    public Class<T> getEventType() {
        return eventType;
    }

    /**
     * @param eventType 事件负载类型
     */
    public void setEventType(Class<T> eventType) {
        this.eventType = eventType;
    }

    /**
     * @return 事件处理器
     */
    public StreamEventHandler<T> getHandler() {
        return handler;
    }

    /**
     * @param handler 事件处理器
     */
    public void setHandler(StreamEventHandler<T> handler) {
        this.handler = handler;
    }

    /**
     * @return SpEL 条件表达式
     */
    public String getCondition() {
        return condition;
    }

    /**
     * @param condition SpEL 条件表达式
     */
    public void setCondition(String condition) {
        this.condition = condition;
    }

    /**
     * @return 目标 destination
     */
    public String getDestination() {
        return destination;
    }

    /**
     * @param destination 目标 destination
     */
    public void setDestination(String destination) {
        this.destination = destination;
    }

    /**
     * @return 消费者组名
     */
    public String getGroup() {
        return group;
    }

    /**
     * @param group 消费者组名
     */
    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * 创建一个 Builder 以便链式构建注册信息。
     *
     * @param <T> 负载类型
     * @return 新的 Builder 实例
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * ListenerRegistration 的链式构建器。
     *
     * @param <T> 事件负载类型
     */
    public static class Builder<T> {
        private String listenerId;
        private Class<T> eventType;
        private StreamEventHandler<T> handler;
        private String condition;
        private String destination;
        private String group;

        /**
         * @param listenerId 监听器唯一 ID
         * @return 当前 Builder
         */
        public Builder<T> listenerId(String listenerId) {
            this.listenerId = listenerId;
            return this;
        }

        /**
         * @param eventType 事件负载类型
         * @return 当前 Builder
         */
        public Builder<T> eventType(Class<T> eventType) {
            this.eventType = eventType;
            return this;
        }

        /**
         * @param handler 事件处理器
         * @return 当前 Builder
         */
        public Builder<T> handler(StreamEventHandler<T> handler) {
            this.handler = handler;
            return this;
        }

        /**
         * @param condition SpEL 条件表达式
         * @return 当前 Builder
         */
        public Builder<T> condition(String condition) {
            this.condition = condition;
            return this;
        }

        /**
         * @param destination 目标 destination
         * @return 当前 Builder
         */
        public Builder<T> destination(String destination) {
            this.destination = destination;
            return this;
        }

        /**
         * @param group 消费者组名
         * @return 当前 Builder
         */
        public Builder<T> group(String group) {
            this.group = group;
            return this;
        }

        /**
         * 终结方法，生成 {@link ListenerRegistration} 实例。
         *
         * @return 新构建的注册信息
         */
        public ListenerRegistration<T> build() {
            return new ListenerRegistration<>(listenerId, eventType, handler, condition, destination, group);
        }
    }
}
