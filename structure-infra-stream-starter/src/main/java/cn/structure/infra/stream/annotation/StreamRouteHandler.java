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
 * 声明式路由处理器注解，标注在方法上即自动注册为 {@code StreamEventRouter} 的路由。
 *
 * <p>设计意图：
 * <ul>
 *   <li>提供面向"事件类型 + 业务类型"的统一路由编程模型，与 Spring Cloud Stream 的 binding 模型解耦</li>
 *   <li>通过 {@link cn.structure.infra.stream.router.RouteHandlerBeanPostProcessor} 在 Bean 初始化后
 *       自动扫描注解方法，并调用 {@code StreamEventRouter#registerRoute} 完成注册</li>
 *   <li>路由匹配遵循 4 步规则：eventType 精确匹配 → businessType 通配匹配 → payloadType 类型匹配 → SpEL condition 条件求值</li>
 * </ul>
 *
 * <p>协作关系：
 * <ul>
 *   <li>{@link #eventType()} 必填，作为路由分发的第一维索引</li>
 *   <li>{@link #businessType()} 可选，支持 {@code "*"} 通配符，作为第二维筛选条件</li>
 *   <li>{@link #condition()} 可选，使用 SpEL 表达式，对 payload 进行精细化条件求值</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * @StreamRouteHandler(eventType = "order", businessType = "create")
 * public void handleCreateOrder(OrderPayload payload) { ... }
 * }</pre>
 *
 * @see cn.structure.infra.stream.router.StreamEventRouter
 * @see cn.structure.infra.stream.router.RouteHandlerBeanPostProcessor
 * @see StreamEventListener
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StreamRouteHandler {

    /**
     * 事件类型的快捷属性，与 {@link #eventType()} 互为别名。
     * <p>当仅需指定 eventType 时使用，例如 <code>@StreamRouteHandler("order")</code>。
     *
     * @return 事件类型，默认空字符串
     */
    @AliasFor("eventType")
    String value() default "";

    /**
     * 事件类型，作为路由分发的第一维索引键，<b>必填</b>。
     * <p>路由器内部以 eventType 作为 Map key 索引所有候选路由。
     *
     * @return 事件类型，默认空字符串（运行时由框架校验非空）
     */
    @AliasFor("value")
    String eventType() default "";

    /**
     * 业务类型，作为路由匹配的第二维筛选条件，可选。
     * <p>支持 {@code "*"} 通配符表示匹配任意 businessType；留空同样表示不参与匹配。
     *
     * @return 业务类型或通配符，默认空字符串
     */
    String businessType() default "";

    /**
     * SpEL 条件表达式，作为路由匹配的第四步精细化筛选，可选。
     * <p>表达式中可通过 {@code #payload} 引用事件负载对象，
     * 例如 <code>#payload.amount &gt; 100</code>。
     *
     * @return SpEL 条件表达式，默认空字符串表示无条件
     */
    String condition() default "";

}
