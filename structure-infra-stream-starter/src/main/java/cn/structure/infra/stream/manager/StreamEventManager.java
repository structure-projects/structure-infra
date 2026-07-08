package cn.structure.infra.stream.manager;

import cn.structure.infra.stream.handler.StreamEventHandler;
import cn.structure.infra.stream.properties.StreamProperties;

import java.util.List;
import java.util.Map;

/**
 * 事件管理器 SPI，统一管理传统消息监听模型下的 binding 与 listener 生命周期。
 *
 * <p>设计意图：
 * <ul>
 *   <li>封装 Spring Cloud Stream 的 {@code StreamBridge} 投递能力，对外暴露统一发布 API</li>
 *   <li>维护 binding 元数据（destination/group/content-type/concurrency），支持运行时动态注册</li>
 *   <li>维护每个 binding 下的监听器列表，提供 eventType 类型过滤与 SpEL condition 条件求值</li>
 * </ul>
 *
 * <p>协作关系：
 * <ul>
 *   <li>由 {@link DefaultStreamEventManagerImpl} 提供默认实现</li>
 *   <li>由 {@code EventListenerBeanPostProcessor} 在扫描 {@code @StreamEventListener} 时调用注册 API</li>
 *   <li>binding 元数据来源于 {@link StreamProperties#getBindings()}，并与全局默认值兜底配合</li>
 * </ul>
 *
 * @see DefaultStreamEventManagerImpl
 * @see ListenerRegistration
 */
public interface StreamEventManager {

    /**
     * 向指定 binding 发布事件，使用 binding 自身配置的 destination 与 group。
     *
     * @param bindingName 绑定名称
     * @param event       事件负载
     * @param <T>         负载类型
     * @throws IllegalArgumentException 当 bindingName 未注册时抛出
     */
    <T> void publish(String bindingName, T event);

    /**
     * 向指定 binding 与 destination 发布事件，group 取全局 default-group。
     *
     * @param bindingName 绑定名称
     * @param destination 目标 destination
     * @param event       事件负载
     * @param <T>         负载类型
     */
    <T> void publish(String bindingName, String destination, T event);

    /**
     * 向指定 binding、destination、group 发布事件。
     * <p>若 binding 尚未注册，会触发动态 binding 注册。
     *
     * @param bindingName 绑定名称
     * @param destination 目标 destination
     * @param group       消费者组
     * @param event       事件负载
     * @param <T>         负载类型
     */
    <T> void publish(String bindingName, String destination, String group, T event);

    /**
     * 注册监听器到指定 binding，使用 binding 自身的 destination/group，无 SpEL 条件。
     *
     * @param bindingName 绑定名称
     * @param eventType   事件负载类型，dispatch 时据此过滤
     * @param handler     事件处理器
     * @param <T>         负载类型
     * @throws IllegalArgumentException 当 bindingName 未注册时抛出
     */
    <T> void registerListener(String bindingName, Class<T> eventType, StreamEventHandler<T> handler);

    /**
     * 注册监听器到指定 binding，附加 SpEL 条件，使用 binding 自身的 destination/group。
     *
     * @param bindingName 绑定名称
     * @param eventType   事件负载类型
     * @param condition   SpEL 条件表达式，可通过 {@code #event} 引用事件，留空表示无条件
     * @param handler     事件处理器
     * @param <T>         负载类型
     * @throws IllegalArgumentException 当 bindingName 未注册时抛出
     */
    <T> void registerListener(String bindingName, Class<T> eventType, String condition, StreamEventHandler<T> handler);

    /**
     * 注册监听器到指定 binding，显式指定 destination/group，无 SpEL 条件。
     *
     * @param bindingName 绑定名称
     * @param destination 目标 destination
     * @param group       消费者组
     * @param eventType   事件负载类型
     * @param handler     事件处理器
     * @param <T>         负载类型
     */
    <T> void registerListener(String bindingName, String destination, String group, Class<T> eventType, StreamEventHandler<T> handler);

    /**
     * 注册监听器到指定 binding，显式指定 destination/group，附加 SpEL 条件。
     *
     * @param bindingName 绑定名称
     * @param destination 目标 destination
     * @param group       消费者组
     * @param eventType   事件负载类型
     * @param condition   SpEL 条件表达式，留空表示无条件
     * @param handler     事件处理器
     * @param <T>         负载类型
     */
    <T> void registerListener(String bindingName, String destination, String group, Class<T> eventType, String condition, StreamEventHandler<T> handler);

    /**
     * 注销指定 binding 下的全部监听器。
     *
     * @param bindingName 绑定名称
     */
    void unregisterListener(String bindingName);

    /**
     * 按 listenerId 精确注销监听器。
     *
     * @param bindingName 绑定名称
     * @param listenerId  监听器唯一 ID（注册时由 UUID 生成）
     */
    void unregisterListener(String bindingName, String listenerId);

    /**
     * 判断指定 binding 下是否存在已注册的监听器。
     *
     * @param bindingName 绑定名称
     * @return 存在且非空返回 true
     */
    boolean isListenerRegistered(String bindingName);

    /**
     * 将事件派发到指定 binding 下的所有匹配监听器。
     * <p>派发规则：eventType 类型过滤 → SpEL condition 条件求值 → 回调 handler。
     *
     * @param bindingName 绑定名称
     * @param event       事件负载
     * @param <T>         负载类型
     */
    <T> void dispatch(String bindingName, T event);

    /**
     * @param bindingName 绑定名称
     * @return 该 binding 下的所有监听器注册信息，不存在时返回空列表
     */
    List<ListenerRegistration<?>> getListeners(String bindingName);

    /**
     * 动态注册 binding，group 使用全局 default-group。
     *
     * @param bindingName 绑定名称
     * @param destination 目标 destination
     */
    void registerBinding(String bindingName, String destination);

    /**
     * 动态注册 binding，显式指定 group。
     *
     * @param bindingName 绑定名称
     * @param destination 目标 destination
     * @param group       消费者组
     */
    void registerBinding(String bindingName, String destination, String group);

    /**
     * 动态注册 binding，显式指定全部参数。
     * <p>已存在时打印告警并跳过，保证幂等。
     *
     * @param bindingName 绑定名称
     * @param destination 目标 destination
     * @param group       消费者组
     * @param contentType 内容类型
     * @param concurrency 消费并发数
     */
    void registerBinding(String bindingName, String destination, String group, String contentType, Integer concurrency);

    /**
     * 注销 binding，同时联动注销其下所有监听器。
     *
     * @param bindingName 绑定名称
     */
    void unregisterBinding(String bindingName);

    /**
     * 判断指定 binding 是否已注册。
     *
     * @param bindingName 绑定名称
     * @return 已注册返回 true
     */
    boolean isBindingRegistered(String bindingName);

    /**
     * 获取指定 binding 的元数据。
     *
     * @param bindingName 绑定名称
     * @return binding 元数据，不存在返回 null
     */
    StreamProperties.Binding getBinding(String bindingName);

    /**
     * @return 当前所有已注册的 binding 元数据 Map
     */
    Map<String, StreamProperties.Binding> getAllBindings();

}
