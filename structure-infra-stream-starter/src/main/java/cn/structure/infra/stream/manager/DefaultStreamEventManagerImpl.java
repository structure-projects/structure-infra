package cn.structure.infra.stream.manager;

import cn.structure.infra.stream.handler.StreamEventHandler;
import cn.structure.infra.stream.properties.StreamProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link StreamEventManager} 的默认实现，整合 Spring Cloud Stream 的 {@link StreamBridge} 投递能力
 * 与本地监听器注册表。
 *
 * <p>设计意图：
 * <ul>
 *   <li>以 {@link ConcurrentHashMap} 维护 binding → 监听器列表的映射，保证并发注册/派发的线程安全</li>
 *   <li>使用 {@link SpelExpressionParser} 对监听器 condition 求值，实现按条件过滤派发</li>
 *   <li>binding 元数据写入 {@link StreamProperties#getBindings()}，与 publish/dispatch 共享同一份配置</li>
 * </ul>
 *
 * <p>协作关系：
 * <ul>
 *   <li>publish：通过 {@code StreamBridge.send} 投递到 <code>{bindingName}-out-0</code> 输出通道</li>
 *   <li>dispatch：在本地内存中迭代监听器并回调，不经过消息中间件</li>
 *   <li>动态 binding 注册：当 publish 时发现 binding 缺失，会自动补注册</li>
 * </ul>
 *
 * @see StreamEventManager
 * @see ListenerRegistration
 */
public class DefaultStreamEventManagerImpl implements StreamEventManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultStreamEventManagerImpl.class);

    /**
     * Spring Cloud Stream 的桥接器，用于将消息发送到输出 binding。
     */
    private final StreamBridge streamBridge;
    /**
     * stream 主配置，提供 binding 元数据与全局默认值兜底。
     */
    private final StreamProperties streamProperties;
    /**
     * 监听器注册表：bindingName → 该 binding 下的所有监听器注册信息列表。
     */
    private final Map<String, List<ListenerRegistration<?>>> registeredListeners = new ConcurrentHashMap<>();
    /**
     * SpEL 表达式解析器，用于对监听器的 condition 进行求值。
     */
    private final SpelExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * 构造方法，由 {@code StreamAutoConfiguration} 注入依赖。
     *
     * @param streamBridge     Spring Cloud Stream 桥接器
     * @param streamProperties stream 主配置
     */
    public DefaultStreamEventManagerImpl(StreamBridge streamBridge, StreamProperties streamProperties) {
        this.streamBridge = streamBridge;
        this.streamProperties = streamProperties;
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现说明：从 {@link StreamProperties#getBindings()} 查找 binding 元数据，
     * 未找到时抛出 {@link IllegalArgumentException}，找到后委托给
     * {@link #publish(String, String, String, Object)} 完成实际投递。
     */
    @Override
    public <T> void publish(String bindingName, T event) {
        StreamProperties.Binding binding = streamProperties.getBindings().get(bindingName);
        if (binding == null) {
            throw new IllegalArgumentException("Binding not found: " + bindingName);
        }
        publish(bindingName, binding.getDestination(), binding.getGroup(), event);
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现说明：group 取自全局 {@code default-group}。
     */
    @Override
    public <T> void publish(String bindingName, String destination, T event) {
        String group = streamProperties.getDefaultGroup();
        publish(bindingName, destination, group, event);
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现说明：
     * <ol>
     *   <li>调用 {@link #ensureBindingRegistered} 保证 binding 已注册（动态 binding 注册）</li>
     *   <li>构建 {@link Message}，通过 {@link StreamBridge#send} 投递到 <code>{bindingName}-out-0</code> 输出通道</li>
     * </ol>
     */
    @Override
    public <T> void publish(String bindingName, String destination, String group, T event) {
        // 动态 binding 注册：若 binding 缺失则补注册，确保 publish 链路可用
        ensureBindingRegistered(bindingName, destination, group);

        // Spring Cloud Stream 约定：输出 binding 名为 {bindingName}-out-0
        String outputBindingName = bindingName + "-out-0";
        Message<T> message = MessageBuilder.withPayload(event).build();
        streamBridge.send(outputBindingName, message);
        log.debug("Published event to binding: {}, destination: {}, group: {}", outputBindingName, destination, group);
    }

    /**
     * 保证指定 binding 在 StreamProperties 中已注册，未注册时调用 {@link #registerBinding} 补注册。
     *
     * @param bindingName 绑定名称
     * @param destination 目标 destination
     * @param group       消费者组
     */
    private synchronized void ensureBindingRegistered(String bindingName, String destination, String group) {
        if (!streamProperties.getBindings().containsKey(bindingName)) {
            registerBinding(bindingName, destination, group);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> void registerListener(String bindingName, Class<T> eventType, StreamEventHandler<T> handler) {
        registerListener(bindingName, eventType, "", handler);
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现说明：从 {@link StreamProperties#getBindings()} 查找 binding 元数据，
     * 取其 destination/group 委托给四参版本 {@link #registerListener(String, String, String, Class, String, StreamEventHandler)}。
     */
    @Override
    public <T> void registerListener(String bindingName, Class<T> eventType, String condition, StreamEventHandler<T> handler) {
        StreamProperties.Binding binding = streamProperties.getBindings().get(bindingName);
        if (binding == null) {
            throw new IllegalArgumentException("Binding not found: " + bindingName);
        }
        registerListener(bindingName, binding.getDestination(), binding.getGroup(), eventType, condition, handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> void registerListener(String bindingName, String destination, String group, Class<T> eventType, StreamEventHandler<T> handler) {
        registerListener(bindingName, destination, group, eventType, "", handler);
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现说明：生成 UUID 作为 listenerId，构建 {@link ListenerRegistration} 并加入注册表，
     * 使用 {@link ConcurrentHashMap#computeIfAbsent} 保证并发安全追加。
     */
    @Override
    public <T> void registerListener(String bindingName, String destination, String group, Class<T> eventType, String condition, StreamEventHandler<T> handler) {
        // 生成唯一 listenerId，便于后续按 ID 精确注销
        String listenerId = UUID.randomUUID().toString();
        ListenerRegistration<T> registration = ListenerRegistration.<T>builder()
                .listenerId(listenerId)
                .eventType(eventType)
                .handler(handler)
                .condition(condition)
                .destination(destination)
                .group(group)
                .build();

        // 并发安全地追加到 binding 对应的监听器列表
        registeredListeners.computeIfAbsent(bindingName, k -> new ArrayList<>()).add(registration);

        log.info("Registered listener for binding: {}, listenerId: {}, destination: {}, group: {}, eventType: {}, condition: {}",
                bindingName, listenerId, destination, group, eventType.getName(), condition);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unregisterListener(String bindingName) {
        registeredListeners.remove(bindingName);
        log.info("Unregistered all listeners for binding: {}", bindingName);
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现说明：在 binding 列表中按 listenerId 过滤移除；列表变空时联动从 Map 中移除该 binding 条目。
     */
    @Override
    public void unregisterListener(String bindingName, String listenerId) {
        List<ListenerRegistration<?>> registrations = registeredListeners.get(bindingName);
        if (registrations != null) {
            boolean removed = registrations.removeIf(r -> r.getListenerId().equals(listenerId));
            if (removed) {
                log.info("Unregistered listener: {} for binding: {}", listenerId, bindingName);
            }
            // 列表为空时清理 Map 条目，避免留下空 entry
            if (registrations.isEmpty()) {
                registeredListeners.remove(bindingName);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isListenerRegistered(String bindingName) {
        return registeredListeners.containsKey(bindingName) && !registeredListeners.get(bindingName).isEmpty();
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现说明：迭代 binding 下的所有监听器，依次执行两步过滤：
     * <ol>
     *   <li>eventType 类型过滤：{@code registration.getEventType().isInstance(event)}</li>
     *   <li>SpEL condition 求值：通过 {@link #matchesCondition} 判断</li>
     * </ol>
     * 命中后回调 handler，异常被捕获并打印日志，不影响后续监听器执行。
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> void dispatch(String bindingName, T event) {
        List<ListenerRegistration<?>> registrations = registeredListeners.get(bindingName);
        if (registrations == null || registrations.isEmpty()) {
            log.debug("No listeners registered for binding: {}", bindingName);
            return;
        }

        for (ListenerRegistration<?> registration : registrations) {
            // 第 1 步：eventType 类型过滤
            if (!registration.getEventType().isInstance(event)) {
                continue;
            }

            // 第 2 步：SpEL condition 条件求值
            if (matchesCondition(registration.getCondition(), event)) {
                try {
                    ((StreamEventHandler<T>) registration.getHandler()).handle(event);
                    log.debug("Dispatched event to listener: {} for binding: {}", registration.getListenerId(), bindingName);
                } catch (Exception e) {
                    // 单个监听器异常不影响其他监听器执行
                    log.error("Error handling event in listener: {} for binding: {}", registration.getListenerId(), bindingName, e);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ListenerRegistration<?>> getListeners(String bindingName) {
        return registeredListeners.getOrDefault(bindingName, new ArrayList<>());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerBinding(String bindingName, String destination) {
        registerBinding(bindingName, destination, streamProperties.getDefaultGroup());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerBinding(String bindingName, String destination, String group) {
        registerBinding(bindingName, destination, group, streamProperties.getDefaultContentType(), streamProperties.getDefaultConcurrency());
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现说明：使用 synchronized 保证并发注册的幂等性，已存在时仅打印告警并返回。
     * contentType/concurrency 为 null 时分别回退到全局默认值。
     */
    @Override
    public void registerBinding(String bindingName, String destination, String group, String contentType, Integer concurrency) {
        synchronized (this) {
            if (streamProperties.getBindings().containsKey(bindingName)) {
                log.warn("Binding already registered: {}", bindingName);
                return;
            }

            StreamProperties.Binding binding = new StreamProperties.Binding();
            binding.setDestination(destination);
            binding.setGroup(group);
            // contentType/concurrency 为 null 时回退到全局默认值
            binding.setContentType(contentType != null ? contentType : streamProperties.getDefaultContentType());
            binding.setConcurrency(concurrency != null ? concurrency : streamProperties.getDefaultConcurrency());

            streamProperties.getBindings().put(bindingName, binding);

            log.info("Dynamically registered binding: {}, destination: {}, group: {}, contentType: {}",
                    bindingName, destination, group, binding.getContentType());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现说明：从 {@link StreamProperties#getBindings()} 移除 binding 元数据，
     * 并联动调用 {@link #unregisterListener} 清理其下所有监听器。
     */
    @Override
    public void unregisterBinding(String bindingName) {
        synchronized (this) {
            StreamProperties.Binding removed = streamProperties.getBindings().remove(bindingName);
            if (removed != null) {
                // 联动清理监听器，避免悬挂引用
                unregisterListener(bindingName);
                log.info("Dynamically unregistered binding: {}", bindingName);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBindingRegistered(String bindingName) {
        return streamProperties.getBindings().containsKey(bindingName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StreamProperties.Binding getBinding(String bindingName) {
        return streamProperties.getBindings().get(bindingName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, StreamProperties.Binding> getAllBindings() {
        return streamProperties.getBindings();
    }

    /**
     * 对监听器 condition 进行 SpEL 求值，判断是否派发当前事件。
     *
     * @param condition SpEL 条件表达式，null 或空串表示无条件（恒为 true）
     * @param event     事件负载，作为 SpEL 上下文中的 {@code #event} 变量
     * @param <T>       负载类型
     * @return 求值为 true 返回 true；求值异常返回 false，避免抛出中断派发
     */
    private <T> boolean matchesCondition(String condition, T event) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }

        try {
            Expression expression = expressionParser.parseExpression(condition);
            EvaluationContext context = new StandardEvaluationContext();
            // 将事件作为 #event 变量暴露给 SpEL 表达式
            context.setVariable("event", event);
            Boolean result = expression.getValue(context, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Failed to evaluate condition: {} for event: {}", condition, event, e);
            return false;
        }
    }
}
