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

package cn.structure.infra.stream.event;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一事件信封（Event Envelope），承载路由器路由所需的所有元数据与业务负载。
 *
 * <p>设计意图：
 * <ul>
 *   <li>作为 {@code StreamEventRouter} 与 {@code StreamEventManager} 的统一传输载体，
 *       将路由元数据（eventType/businessType）与业务负载（payload）解耦</li>
 *   <li>提供 {@link #eventId}、{@link #traceId} 等追踪字段，便于全链路日志关联</li>
 *   <li>headers 字段提供扩展能力，承载自定义元信息而不污染 payload 结构</li>
 * </ul>
 *
 * <p>协作关系：
 * <ul>
 *   <li>由生产者通过 {@link #of(String, Object)} 或 {@link #builder()} 构建后投递</li>
 *   <li>由 {@code StreamEventRouter#route} 解析 eventType/businessType/payload 后分发至匹配的处理器</li>
 *   <li>泛型 <code>T</code> 表示业务负载类型，便于路由器做 payloadType 类型匹配（路由第 3 步）</li>
 * </ul>
 *
 * @param <T> 业务负载类型
 */
public class StreamEvent<T> {

    /**
     * 事件唯一标识，默认由 UUID 自动生成，用于事件去重与全链路追踪。
     */
    private String eventId;
    /**
     * 事件类型，作为路由匹配第 1 步的精确匹配键，与 {@code StreamEventRouter} 内部 Map key 对应。
     */
    private String eventType;
    /**
     * 业务类型，作为路由匹配第 2 步的筛选条件，支持 {@code "*"} 通配符匹配。
     */
    private String businessType;
    /**
     * 事件来源标识，可用于区分生产系统/模块。
     */
    private String source;
    /**
     * 事件发生时间戳。
     */
    private LocalDateTime timestamp;
    /**
     * 业务负载，作为路由匹配第 3 步 payloadType 类型检查的目标对象，
     * 也是 SpEL condition 表达式中 {@code #payload} 变量所引用的对象。
     */
    private T payload;
    /**
     * 自定义消息头，承载不影响路由匹配的扩展元信息。
     */
    private Map<String, String> headers = new HashMap<>();
    /**
     * 链路追踪 ID，用于跨服务日志关联。
     */
    private String traceId;

    /**
     * 默认构造方法，供框架反序列化或 Builder 使用。
     */
    public StreamEvent() {
    }

    /**
     * 全参构造方法，构建完整的事件信封。
     *
     * @param eventId       事件唯一标识
     * @param eventType     事件类型
     * @param businessType  业务类型，可为 null
     * @param source        事件来源
     * @param timestamp     事件时间戳
     * @param payload       业务负载
     * @param headers       自定义消息头，为 null 时使用空 Map
     * @param traceId       链路追踪 ID
     */
    public StreamEvent(String eventId, String eventType, String businessType, String source,
                       LocalDateTime timestamp, T payload, Map<String, String> headers, String traceId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.businessType = businessType;
        this.source = source;
        this.timestamp = timestamp;
        this.payload = payload;
        this.headers = headers != null ? headers : new HashMap<>();
        this.traceId = traceId;
    }

    /**
     * @return 事件唯一标识
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * @param eventId 事件唯一标识
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * @return 事件类型，路由匹配第 1 步键
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * @param eventType 事件类型
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * @return 业务类型，路由匹配第 2 步筛选条件
     */
    public String getBusinessType() {
        return businessType;
    }

    /**
     * @param businessType 业务类型
     */
    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    /**
     * @return 事件来源标识
     */
    public String getSource() {
        return source;
    }

    /**
     * @param source 事件来源
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * @return 事件时间戳
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * @param timestamp 事件时间戳
     */
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * @return 业务负载
     */
    public T getPayload() {
        return payload;
    }

    /**
     * @param payload 业务负载
     */
    public void setPayload(T payload) {
        this.payload = payload;
    }

    /**
     * @return 自定义消息头 Map，永不为 null
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * @param headers 自定义消息头，为 null 时置为空 Map
     */
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers != null ? headers : new HashMap<>();
    }

    /**
     * @return 链路追踪 ID
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * @param traceId 链路追踪 ID
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * 快捷工厂方法：仅指定 eventType 与 payload，自动生成 UUID、当前时间戳、空 headers。
     * <p>businessType/source/traceId 均为 null，适用于不参与业务类型筛选的简单场景。
     *
     * @param eventType 事件类型
     * @param payload   业务负载
     * @param <T>       负载类型
     * @return 新构建的 StreamEvent 信封
     */
    public static <T> StreamEvent<T> of(String eventType, T payload) {
        return new StreamEvent<>(
                java.util.UUID.randomUUID().toString(),
                eventType,
                null,
                null,
                LocalDateTime.now(),
                payload,
                new HashMap<>(),
                null
        );
    }

    /**
     * 快捷工厂方法：指定 eventType、businessType 与 payload，自动生成 UUID、当前时间戳、空 headers。
     * <p>适用于参与 businessType 路由筛选的场景。
     *
     * @param eventType    事件类型
     * @param businessType 业务类型
     * @param payload      业务负载
     * @param <T>          负载类型
     * @return 新构建的 StreamEvent 信封
     */
    public static <T> StreamEvent<T> of(String eventType, String businessType, T payload) {
        return new StreamEvent<>(
                java.util.UUID.randomUUID().toString(),
                eventType,
                businessType,
                null,
                LocalDateTime.now(),
                payload,
                new HashMap<>(),
                null
        );
    }

    /**
     * 创建一个 Builder 以便逐步构建复杂事件信封。
     *
     * @param <T> 负载类型
     * @return 新的 Builder 实例
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * StreamEvent 的链式构建器，用于灵活组装事件信封的各字段。
     *
     * @param <T> 业务负载类型
     */
    public static class Builder<T> {
        private String eventId;
        private String eventType;
        private String businessType;
        private String source;
        private LocalDateTime timestamp;
        private T payload;
        private Map<String, String> headers = new HashMap<>();
        private String traceId;

        /**
         * @param eventId 事件唯一标识
         * @return 当前 Builder
         */
        public Builder<T> eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        /**
         * @param eventType 事件类型
         * @return 当前 Builder
         */
        public Builder<T> eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        /**
         * @param businessType 业务类型
         * @return 当前 Builder
         */
        public Builder<T> businessType(String businessType) {
            this.businessType = businessType;
            return this;
        }

        /**
         * @param source 事件来源
         * @return 当前 Builder
         */
        public Builder<T> source(String source) {
            this.source = source;
            return this;
        }

        /**
         * @param timestamp 事件时间戳
         * @return 当前 Builder
         */
        public Builder<T> timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * @param payload 业务负载
         * @return 当前 Builder
         */
        public Builder<T> payload(T payload) {
            this.payload = payload;
            return this;
        }

        /**
         * @param headers 自定义消息头
         * @return 当前 Builder
         */
        public Builder<T> headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        /**
         * @param traceId 链路追踪 ID
         * @return 当前 Builder
         */
        public Builder<T> traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        /**
         * 终结方法，生成不可变 StreamEvent 实例。
         *
         * @return 新构建的 StreamEvent
         */
        public StreamEvent<T> build() {
            return new StreamEvent<>(eventId, eventType, businessType, source, timestamp, payload, headers, traceId);
        }
    }
}
