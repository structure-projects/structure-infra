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

package cn.structure.infra.stream.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * stream 主配置属性，对应 YAML 配置项 <code>structure.infra.stream</code>。
 *
 * <p>设计意图：
 * <ul>
 *   <li>集中管理 stream 模块的全局开关与默认值（enabled/auto-binding/default-group/default-content-type/default-binder/default-concurrency）</li>
 *   <li>维护动态注册的 binding 元数据 Map：{@link #bindings}，key 为 bindingName，value 为 {@link Binding}</li>
 *   <li>作为 {@code StreamEventManager} 与 {@code EventListenerBeanPostProcessor} 的共享配置中心</li>
 * </ul>
 *
 * <p>协作关系：
 * <ul>
 *   <li>由 {@code StreamAutoConfiguration} 通过 {@code @EnableConfigurationProperties} 启用</li>
 *   <li>动态 binding 注册时写入 {@link #bindings}；publish/dispatch 时读取 {@link #bindings}</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "structure.infra.stream")
public class StreamProperties {

    /**
     * 全局开关，控制是否启用 stream 模块，默认 true。
     */
    private boolean enabled = true;
    /**
     * 是否启用自动 binding 注册（由 {@code StreamBindingBeanFactoryPostProcessor} 处理），默认 true。
     */
    private boolean autoBinding = true;
    /**
     * 默认消费者组名，binding 未显式声明 group 时使用，默认 "default"。
     */
    private String defaultGroup = "default";
    /**
     * 默认内容类型，binding 未显式声明 content-type 时使用，默认 "application/json"。
     */
    private String defaultContentType = "application/json";
    /**
     * 默认 binder 名称，binding 未显式声明 binder 时使用，null 表示使用 Spring Cloud Stream 默认 binder。
     */
    private String defaultBinder;
    /**
     * 默认消费并发数，binding 未显式声明 concurrency 时使用，默认 1。
     */
    private Integer defaultConcurrency = 1;
    /**
     * binding 元数据 Map：bindingName → Binding 配置。
     * <p>初始来自 YAML，运行时可由 {@code StreamEventManager#registerBinding} 动态追加。
     */
    private Map<String, Binding> bindings = new HashMap<>();

    /**
     * @return 是否启用 stream 模块
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled 是否启用 stream 模块
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return 是否启用自动 binding 注册
     */
    public boolean isAutoBinding() {
        return autoBinding;
    }

    /**
     * @param autoBinding 是否启用自动 binding 注册
     */
    public void setAutoBinding(boolean autoBinding) {
        this.autoBinding = autoBinding;
    }

    /**
     * @return 默认消费者组名
     */
    public String getDefaultGroup() {
        return defaultGroup;
    }

    /**
     * @param defaultGroup 默认消费者组名
     */
    public void setDefaultGroup(String defaultGroup) {
        this.defaultGroup = defaultGroup;
    }

    /**
     * @return 默认内容类型
     */
    public String getDefaultContentType() {
        return defaultContentType;
    }

    /**
     * @param defaultContentType 默认内容类型
     */
    public void setDefaultContentType(String defaultContentType) {
        this.defaultContentType = defaultContentType;
    }

    /**
     * @return 默认 binder 名称
     */
    public String getDefaultBinder() {
        return defaultBinder;
    }

    /**
     * @param defaultBinder 默认 binder 名称
     */
    public void setDefaultBinder(String defaultBinder) {
        this.defaultBinder = defaultBinder;
    }

    /**
     * @return 默认消费并发数
     */
    public Integer getDefaultConcurrency() {
        return defaultConcurrency;
    }

    /**
     * @param defaultConcurrency 默认消费并发数
     */
    public void setDefaultConcurrency(Integer defaultConcurrency) {
        this.defaultConcurrency = defaultConcurrency;
    }

    /**
     * @return binding 元数据 Map
     */
    public Map<String, Binding> getBindings() {
        return bindings;
    }

    /**
     * @param bindings binding 元数据 Map
     */
    public void setBindings(Map<String, Binding> bindings) {
        this.bindings = bindings;
    }

    /**
     * 获取指定 binding 的元数据，不存在时自动创建空 Binding 并放入 Map。
     * <p>注意：此方法具有副作用，与 {@link #getBindings()}.get(key) 行为不同。
     *
     * @param bindingName 绑定名称
     * @return 对应的 Binding 元数据（永不为 null）
     */
    public Binding getBinding(String bindingName) {
        return bindings.computeIfAbsent(bindingName, k -> new Binding());
    }

    /**
     * 单个 binding 的元数据。
     *
     * <p>对应 YAML 配置示例：
     * <pre>{@code
     * structure:
     *   infra:
     *     stream:
     *       bindings:
     *         orderListener:
     *           destination: order-exchange
     *           group: order-group
     *           content-type: application/json
     *           concurrency: 2
     * }</pre>
     */
    public static class Binding {
        /**
         * 目标 destination（exchange/topic）。
         */
        private String destination;
        /**
         * 内容类型，默认 "application/json"。
         */
        private String contentType = "application/json";
        /**
         * 消费者组名。
         */
        private String group;
        /**
         * binder 名称，null 表示使用默认 binder。
         */
        private String binder;
        /**
         * 消费并发数，null 表示使用全局默认值。
         */
        private Integer concurrency;
        /**
         * 消费者配置前缀，用于扩展生成 consumer 属性键，默认 "consumer"。
         */
        private String consumerPrefix = "consumer";
        /**
         * 生产者配置前缀，用于扩展生成 producer 属性键，默认 "producer"。
         */
        private String producerPrefix = "producer";

        /**
         * 默认构造方法。
         */
        public Binding() {
        }

        /**
         * 仅指定 destination 的构造方法。
         *
         * @param destination 目标 destination
         */
        public Binding(String destination) {
            this.destination = destination;
        }

        /**
         * 指定 destination 与 group 的构造方法。
         *
         * @param destination 目标 destination
         * @param group       消费者组
         */
        public Binding(String destination, String group) {
            this.destination = destination;
            this.group = group;
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
         * @return 内容类型
         */
        public String getContentType() {
            return contentType;
        }

        /**
         * @param contentType 内容类型
         */
        public void setContentType(String contentType) {
            this.contentType = contentType;
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
         * @return binder 名称
         */
        public String getBinder() {
            return binder;
        }

        /**
         * @param binder binder 名称
         */
        public void setBinder(String binder) {
            this.binder = binder;
        }

        /**
         * @return 消费并发数
         */
        public Integer getConcurrency() {
            return concurrency;
        }

        /**
         * @param concurrency 消费并发数
         */
        public void setConcurrency(Integer concurrency) {
            this.concurrency = concurrency;
        }

        /**
         * @return 消费者配置前缀
         */
        public String getConsumerPrefix() {
            return consumerPrefix;
        }

        /**
         * @param consumerPrefix 消费者配置前缀
         */
        public void setConsumerPrefix(String consumerPrefix) {
            this.consumerPrefix = consumerPrefix;
        }

        /**
         * @return 生产者配置前缀
         */
        public String getProducerPrefix() {
            return producerPrefix;
        }

        /**
         * @param producerPrefix 生产者配置前缀
         */
        public void setProducerPrefix(String producerPrefix) {
            this.producerPrefix = producerPrefix;
        }
    }
}
