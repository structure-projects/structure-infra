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

package cn.structure.infra.stream.processor;

import cn.structure.infra.stream.annotation.StreamEventListener;
import cn.structure.infra.stream.manager.StreamEventManager;
import cn.structure.infra.stream.properties.StreamProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.messaging.Message;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link StreamEventListener} 注解的扫描器与注册器，基于 Spring {@link BeanPostProcessor} 实现。
 *
 * <p>设计意图：
 * <ul>
 *   <li>在 Bean 初始化完成后扫描方法级与类级 {@link StreamEventListener} 注解</li>
 *   <li>解析注解元数据（bindingName/destination/group/eventType/condition），将方法包装为
 *       {@link cn.structure.infra.stream.handler.StreamEventHandler} Lambda 并注册到 {@link StreamEventManager}</li>
 *   <li>同时调用 {@link #ensureBindingRegistered} 将 binding 元数据写入 {@link StreamProperties}，
 *       保证后续 {@code publish} 调用能查到 binding</li>
 * </ul>
 *
 * <p>协作关系：
 * <ul>
 *   <li>依赖 {@link StreamEventManager} SPI 完成实际监听器注册</li>
 *   <li>方法级注解：eventType 默认取方法首个参数类型，bindingName 默认取方法名</li>
 *   <li>类级注解：通过反射调用类的 {@code handle(T)} 方法，bindingName 默认取类名</li>
 * </ul>
 *
 * @see StreamEventListener
 * @see StreamEventManager
 */
public class EventListenerBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(EventListenerBeanPostProcessor.class);

    /**
     * 事件管理器 SPI，用于实际注册监听器。
     */
    private final StreamEventManager streamEventManager;
    /**
     * stream 主配置，提供 binding 元数据与全局默认值兜底。
     */
    private final StreamProperties streamProperties;

    /**
     * 已注册监听器 Bean 缓存：bindingName → bean 实例。
     */
    private final Map<String, Object> listenerBeans = new ConcurrentHashMap<>();

    /**
     * 构造方法，由 {@code StreamAutoConfiguration} 注入依赖。
     *
     * @param streamEventManager 事件管理器 SPI
     * @param streamProperties   stream 主配置
     */
    public EventListenerBeanPostProcessor(StreamEventManager streamEventManager, StreamProperties streamProperties) {
        this.streamEventManager = streamEventManager;
        this.streamProperties = streamProperties;
    }

    /**
     * 在 Bean 初始化完成后扫描 {@link StreamEventListener} 注解并注册监听器。
     *
     * <p>实现说明：
     * <ol>
     *   <li>使用 {@link MethodIntrospector#selectMethods} 查找方法级注解</li>
     *   <li>对每个方法级注解调用 {@link #registerListener(Object, Method, StreamEventListener)} 注册</li>
     *   <li>若类本身标注了 {@link StreamEventListener}，调用 {@link #registerClassListener} 注册类级监听器</li>
     * </ol>
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();
        Map<Method, StreamEventListener> annotatedMethods = MethodIntrospector.selectMethods(targetClass,
                (MethodIntrospector.MetadataLookup<StreamEventListener>) method ->
                        AnnotatedElementUtils.findMergedAnnotation(method, StreamEventListener.class));

        for (Map.Entry<Method, StreamEventListener> entry : annotatedMethods.entrySet()) {
            Method method = entry.getKey();
            StreamEventListener annotation = entry.getValue();
            registerListener(bean, method, annotation);
        }

        // 处理类级注解：类标注 @StreamEventListener 时通过反射调用 handle(T) 方法
        if (targetClass.isAnnotationPresent(StreamEventListener.class)) {
            StreamEventListener annotation = targetClass.getAnnotation(StreamEventListener.class);
            registerClassListener(bean, targetClass, annotation);
        }

        return bean;
    }

    /**
     * 注册方法级监听器。
     *
     * <p>实现说明：
     * <ol>
     *   <li>解析 bindingName（注解显式值或方法名兜底）</li>
     *   <li>解析 eventType（注解显式值或方法首个参数类型兜底）</li>
     *   <li>调用 {@link #ensureBindingRegistered} 保证 binding 元数据存在</li>
     *   <li>根据 destination 是否非空选择注册重载版本，包装方法为 Lambda 反射调用</li>
     * </ol>
     *
     * @param bean       Bean 实例
     * @param method     标注方法
     * @param annotation 注解元数据
     */
    private void registerListener(Object bean, Method method, StreamEventListener annotation) {
        String bindingName = resolveBindingName(annotation);
        String destination = annotation.destination();
        String group = annotation.group();
        Class<?> eventType = annotation.eventType();

        // eventType 默认为 Object.class，回退到方法首个参数类型推断
        if (eventType == Object.class && method.getParameterTypes().length > 0) {
            eventType = method.getParameterTypes()[0];
        }

        // bindingName 为空时回退到方法名
        if (bindingName.isEmpty()) {
            bindingName = method.getName();
        }

        // 确保绑定信息注册到 StreamProperties（供 publish 方法使用）
        ensureBindingRegistered(bindingName, destination, group, annotation.contentType());

        listenerBeans.put(bindingName, bean);

        if (!destination.isEmpty()) {
            streamEventManager.registerListener(bindingName, destination, group, eventType, event -> {
                try {
                    method.invoke(bean, event);
                } catch (Exception e) {
                    log.error("Failed to invoke listener method: {}", method.getName(), e);
                }
            });
        } else {
            streamEventManager.registerListener(bindingName, eventType, event -> {
                try {
                    method.invoke(bean, event);
                } catch (Exception e) {
                    log.error("Failed to invoke listener method: {}", method.getName(), e);
                }
            });
        }

        log.info("Registered listener method: {} for binding: {}", method.getName(), bindingName);
    }

    /**
     * 注册类级监听器，通过反射调用类的 {@code handle(T)} 方法。
     *
     * <p>实现说明：与 {@link #registerListener} 类似，区别在于回调时反射查找 {@code handle(eventType)} 方法。
     *
     * @param bean        Bean 实例
     * @param targetClass Bean 类型
     * @param annotation  注解元数据
     */
    private void registerClassListener(Object bean, Class<?> targetClass, StreamEventListener annotation) {
        String bindingName = resolveBindingName(annotation);
        String destination = annotation.destination();
        String group = annotation.group();
        Class<?> eventType = annotation.eventType();

        // 类级注解的 bindingName 为空时回退到类名
        if (bindingName.isEmpty()) {
            bindingName = targetClass.getSimpleName();
        }

        ensureBindingRegistered(bindingName, destination, group, annotation.contentType());

        listenerBeans.put(bindingName, bean);

        if (!destination.isEmpty()) {
            streamEventManager.registerListener(bindingName, destination, group, eventType, event -> {
                try {
                    Method handleMethod = targetClass.getMethod("handle", eventType);
                    handleMethod.invoke(bean, event);
                } catch (Exception e) {
                    log.error("Failed to invoke listener handle method on class: {}", targetClass.getName(), e);
                }
            });
        } else {
            streamEventManager.registerListener(bindingName, eventType, event -> {
                try {
                    Method handleMethod = targetClass.getMethod("handle", eventType);
                    handleMethod.invoke(bean, event);
                } catch (Exception e) {
                    log.error("Failed to invoke listener handle method on class: {}", targetClass.getName(), e);
                }
            });
        }

        log.info("Registered listener class: {} for binding: {}", targetClass.getName(), bindingName);
    }

    /**
     * 确保绑定信息注册到 StreamProperties，供 publish 方法使用
     */
    private void ensureBindingRegistered(String bindingName, String destination, String group, String contentType) {
        if (!streamProperties.getBindings().containsKey(bindingName)) {
            StreamProperties.Binding binding = new StreamProperties.Binding();
            if (StringUtils.hasText(destination)) {
                binding.setDestination(destination);
            } else {
                // destination 为空时由 bindingName 派生为 {name}-exchange
                binding.setDestination(toDestination(bindingName));
            }
            binding.setGroup(StringUtils.hasText(group) ? group : streamProperties.getDefaultGroup());
            binding.setContentType(StringUtils.hasText(contentType) ? contentType : streamProperties.getDefaultContentType());
            streamProperties.getBindings().put(bindingName, binding);
        }
    }

    /**
     * 将名称转换为 destination，规则：将 {@code .} 与 {@code _} 替换为 {@code -}，转小写后追加 {@code -exchange} 后缀。
     *
     * @param name 原始名称
     * @return 派生的 destination
     */
    private String toDestination(String name) {
        return name.replace(".", "-").replace("_", "-").toLowerCase() + "-exchange";
    }

    /**
     * 解析 bindingName：优先取注解的 {@code bindingName()}，为空时回退到 {@code value()}。
     *
     * @param annotation 注解元数据
     * @return 解析后的 bindingName，可能为空串
     */
    private String resolveBindingName(StreamEventListener annotation) {
        String bindingName = annotation.bindingName();
        if (bindingName.isEmpty()) {
            bindingName = annotation.value();
        }
        return bindingName;
    }

}
