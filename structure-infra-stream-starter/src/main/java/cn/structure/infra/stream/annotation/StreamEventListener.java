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

package cn.structure.infra.stream.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * 传统消息监听注解，用于将方法或类绑定为 Spring Cloud Stream 的消息监听器。
 *
 * <p>设计意图：
 * <ul>
 *   <li>提供与 Spring Cloud Stream 原生 <code>@Bean Function/Consumer</code> 等价的声明式编程模型</li>
 *   <li>通过 {@link EventListenerBeanPostProcessor} 在 Bean 初始化阶段自动扫描注解方法并注册到 {@code StreamEventManager}</li>
 *   <li>通过 {@link StreamBindingBeanFactoryPostProcessor} 在 BeanFactory 阶段自动生成对应的 binding 配置</li>
 * </ul>
 *
 * <p>协作关系：
 * <ul>
 *   <li>与 {@link StreamRouteHandler} 互为补充：本注解面向"按 binding 绑定的传统监听"场景，
 *       而 {@link StreamRouteHandler} 面向"按 eventType/businessType 路由"场景</li>
 *   <li>支持方法级与类级标注；类级标注时需提供 {@code handle(T)} 方法</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * @StreamEventListener("orderListener")
 * public void onOrder(OrderEvent event) { ... }
 * }</pre>
 *
 * @see StreamRouteHandler
 * @see cn.structure.infra.stream.processor.EventListenerBeanPostProcessor
 * @see cn.structure.infra.stream.processor.StreamBindingBeanFactoryPostProcessor
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StreamEventListener {

    /**
     * 绑定名称的快捷属性，与 {@link #bindingName()} 互为别名。
     * <p>当仅指定简单绑定名时使用，例如 <code>@StreamEventListener("orderListener")</code>。
     *
     * @return 绑定名称，默认空字符串
     */
    @AliasFor("bindingName")
    String value() default "";

    /**
     * 显式绑定名称，与 {@link #value()} 互为别名。
     * <p>用于在 {@code StreamProperties.bindings} 中查找或注册对应的 binding 元数据。
     *
     * @return 绑定名称，默认空字符串
     */
    @AliasFor("value")
    String bindingName() default "";

    /**
     * 目标 destination（即 exchange/topic），若不指定则由绑定名自动派生为 <code>{name}-exchange</code>。
     *
     * @return destination 名称，默认空字符串
     */
    String destination() default "";

    /**
     * 消费者组名，用于同组内负载均衡、跨组广播。留空时使用全局 default-group。
     *
     * @return group 名称，默认空字符串
     */
    String group() default "";

    /**
     * 消息内容类型，影响序列化/反序列化行为。
     *
     * @return content-type，默认 {@code application/json}
     */
    String contentType() default "application/json";

    /**
     * 事件负载类型，用于在 {@code StreamEventManager#dispatch} 时进行类型过滤。
     * <p>默认 {@code Object.class} 表示不限制类型，框架将回退到方法首个参数类型推断。
     *
     * @return 事件负载 Class，默认 {@code Object.class}
     */
    Class<?> eventType() default Object.class;

    /**
     * 消费者配置前缀，用于扩展生成 Spring Cloud Stream 的 consumer 属性键。
     *
     * @return consumer 前缀，默认 {@code "consumer"}
     */
    String consumerPrefix() default "consumer";

    /**
     * 生产者配置前缀，用于扩展生成 Spring Cloud Stream 的 producer 属性键。
     *
     * @return producer 前缀，默认 {@code "producer"}
     */
    String producerPrefix() default "producer";

    /**
     * SpEL 条件表达式，仅当表达式求值为 {@code true} 时才会派发到当前监听器。
     * <p>表达式中可通过 {@code #event} 引用事件对象，例如 <code>#event.amount &gt; 100</code>。
     *
     * @return SpEL 条件表达式，默认空字符串表示无条件
     */
    String condition() default "";

}
