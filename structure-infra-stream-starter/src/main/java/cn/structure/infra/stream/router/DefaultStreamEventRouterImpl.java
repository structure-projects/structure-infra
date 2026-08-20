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

package cn.structure.infra.stream.router;

import cn.structure.infra.stream.event.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link StreamEventRouter} 的默认实现，基于内存注册表实现 4 步路由匹配规则。
 *
 * <p>设计意图：
 * <ul>
 *   <li>使用 {@link ConcurrentHashMap} 维护 eventType → 路由列表的映射，保证并发注册/路由的线程安全</li>
 *   <li>使用 {@link SpelExpressionParser} 对 condition 求值，对 payload 进行精细化筛选</li>
 *   <li>使用 {@link java.util.concurrent.atomic.AtomicLong} 生成全局递增 handlerId 后缀，保证唯一性</li>
 * </ul>
 *
 * <p>协作关系：
 * <ul>
 *   <li>route：依次执行 businessType 通配匹配 → payloadType 类型匹配 → SpEL condition 求值</li>
 *   <li>eventType 已作为 Map key 完成第 1 步精确匹配，故 route 内部仅执行第 2~4 步</li>
 *   <li>处理器异常被捕获并打印日志，不影响后续路由执行</li>
 * </ul>
 *
 * @see StreamEventRouter
 * @see RouteRegistration
 */
public class DefaultStreamEventRouterImpl implements StreamEventRouter {

    private static final Logger log = LoggerFactory.getLogger(DefaultStreamEventRouterImpl.class);

    /**
     * 路由注册表：eventType → 该 eventType 下的所有路由注册信息列表。
     * <p>第 1 步 eventType 精确匹配即通过此 Map 的 key 查找完成。
     */
    private final Map<String, List<RouteRegistration<?>>> routeRegistrations = new ConcurrentHashMap<>();
    /**
     * SpEL 表达式解析器，用于对路由 condition 求值（第 4 步）。
     */
    private final SpelExpressionParser expressionParser = new SpelExpressionParser();
    /**
     * 全局递增计数器，用于生成 handlerId 后缀以保证唯一性。
     */
    private final java.util.concurrent.atomic.AtomicLong handlerCounter = new java.util.concurrent.atomic.AtomicLong(0);

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> void registerRoute(String eventType, Class<T> payloadType, StreamRouteHandler<T> handler) {
        registerRoute(eventType, "", payloadType, "", handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> void registerRoute(String eventType, Class<T> payloadType, String condition, StreamRouteHandler<T> handler) {
        registerRoute(eventType, "", payloadType, condition, handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> void registerRoute(String eventType, String businessType, Class<T> payloadType, StreamRouteHandler<T> handler) {
        registerRoute(eventType, businessType, payloadType, "", handler);
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现说明：生成 handlerId，构建 {@link RouteRegistration}，使用
     * {@link ConcurrentHashMap#computeIfAbsent} 保证并发安全追加到 eventType 对应的列表。
     */
    @Override
    public <T> void registerRoute(String eventType, String businessType, Class<T> payloadType,
                                  String condition, StreamRouteHandler<T> handler) {
        String handlerId = generateHandlerId(eventType, businessType, payloadType);
        RouteRegistration<T> registration = new RouteRegistration<>(handlerId, eventType, businessType,
                payloadType, condition, handler);

        // eventType 作为第 1 步精确匹配的 Map key
        routeRegistrations.computeIfAbsent(eventType, k -> new ArrayList<>()).add(registration);

        log.info("Registered route: eventType={}, businessType={}, payloadType={}, condition={}",
                eventType, businessType, payloadType.getName(), condition);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unregisterRoute(String eventType) {
        routeRegistrations.remove(eventType);
        log.info("Unregistered all routes for eventType: {}", eventType);
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现说明：在 eventType 列表中按 handlerId 过滤移除；列表变空时联动从 Map 中移除该 eventType 条目。
     */
    @Override
    public void unregisterRoute(String eventType, String handlerId) {
        List<RouteRegistration<?>> registrations = routeRegistrations.get(eventType);
        if (registrations != null) {
            boolean removed = registrations.removeIf(r -> r.getHandlerId().equals(handlerId));
            if (removed) {
                log.info("Unregistered route: {} for eventType: {}", handlerId, eventType);
            }
            if (registrations.isEmpty()) {
                routeRegistrations.remove(eventType);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现说明：依次执行第 2~4 步匹配：
     * <ol>
     *   <li>第 2 步 businessType 通配匹配：通过 {@link #matchesBusinessType} 判断（支持 {@code "*"} 通配符）</li>
     *   <li>第 3 步 payloadType 类型匹配：{@code registration.getPayloadType().isInstance(event.getPayload())}</li>
     *   <li>第 4 步 SpEL condition 求值：通过 {@link #matchesCondition} 判断</li>
     * </ol>
     * 全部命中后回调 handler，异常被捕获并打印日志，不影响后续路由执行。
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> void route(StreamEvent<T> event) {
        if (event == null || event.getEventType() == null) {
            log.warn("Cannot route null event or event with null eventType");
            return;
        }

        String eventType = event.getEventType();
        // 第 1 步：eventType 精确匹配（Map key 查找）
        List<RouteRegistration<?>> registrations = routeRegistrations.get(eventType);

        if (registrations == null || registrations.isEmpty()) {
            log.debug("No routes registered for eventType: {}", eventType);
            return;
        }

        log.debug("Routing event: eventId={}, eventType={}, businessType={}",
                event.getEventId(), eventType, event.getBusinessType());

        for (RouteRegistration<?> registration : registrations) {
            // 第 2~4 步：businessType 通配匹配 && payloadType 类型匹配 && SpEL condition 条件求值
            if (matchesBusinessType(registration.getBusinessType(), event.getBusinessType()) &&
                    registration.getPayloadType().isInstance(event.getPayload()) &&
                    matchesCondition(registration.getCondition(), event.getPayload())) {
                try {
                    ((StreamRouteHandler<T>) registration.getHandler()).handle(event.getPayload(), event);
                    log.debug("Dispatched event to handler: {} for eventType: {}", registration.getHandlerId(), eventType);
                } catch (Exception e) {
                    // 单个路由处理器异常不影响其他路由执行
                    log.error("Error handling event in handler: {} for eventType: {}", registration.getHandlerId(), eventType, e);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRouteRegistered(String eventType) {
        return routeRegistrations.containsKey(eventType) && !routeRegistrations.get(eventType).isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RouteRegistration<?>> getRoutes(String eventType) {
        return routeRegistrations.getOrDefault(eventType, new ArrayList<>());
    }

    /**
     * 生成全局唯一的 handlerId，格式为 <code>{eventType}:{businessType}:{payloadType}:{counter}</code>。
     *
     * @param eventType    事件类型
     * @param businessType 业务类型，null 时使用 "default"
     * @param payloadType  负载类型
     * @return 唯一 handlerId
     */
    private String generateHandlerId(String eventType, String businessType, Class<?> payloadType) {
        return eventType + ":" + (businessType != null ? businessType : "default") + ":" + payloadType.getSimpleName() + ":" + handlerCounter.incrementAndGet();
    }

    /**
     * businessType 通配符匹配（路由第 2 步）。
     * <p>匹配规则：
     * <ul>
     *   <li>pattern 为 null/空串/{@code "*"} 时视为通配，匹配任意 businessType</li>
     *   <li>否则要求精确相等</li>
     * </ul>
     *
     * @param pattern      路由声明的 businessType 模式
     * @param businessType 事件实际的 businessType
     * @return 匹配返回 true
     */
    private boolean matchesBusinessType(String pattern, String businessType) {
        // 通配符 "*" 或留空均表示匹配任意 businessType
        if (pattern == null || pattern.isEmpty() || "*".equals(pattern)) {
            return true;
        }
        return pattern.equals(businessType);
    }

    /**
     * 对路由 condition 进行 SpEL 求值（路由第 4 步）。
     *
     * @param condition SpEL 条件表达式，null 或空串表示无条件（恒为 true）
     * @param payload   事件负载，作为 SpEL 上下文中的 {@code #payload} 变量
     * @param <T>       负载类型
     * @return 求值为 true 返回 true；求值异常返回 false，避免抛出中断路由
     */
    private <T> boolean matchesCondition(String condition, T payload) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }

        try {
            Expression expression = expressionParser.parseExpression(condition);
            EvaluationContext context = new StandardEvaluationContext();
            // 将负载作为 #payload 变量暴露给 SpEL 表达式
            context.setVariable("payload", payload);
            Boolean result = expression.getValue(context, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Failed to evaluate condition: {} for payload: {}", condition, payload, e);
            return false;
        }
    }
}
