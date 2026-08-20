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
import cn.structure.infra.stream.annotation.StreamRouteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 在 Bean 实例化之前，扫描所有 BeanDefinition 中的 @StreamEventListener 和 @StreamRouteHandler 注解，
 * 自动注册 Spring Cloud Stream 绑定配置和 spring.cloud.function.definition。
 *
 * <p>设计意图：
 * <ul>
 *   <li>在 BeanFactory 阶段（早于 BeanPostProcessor）扫描所有 BeanDefinition，避免 Bean 提前实例化</li>
 *   <li>将自动生成的 binding 配置（destination/group/content-type/concurrency/binder）以
 *       {@link MapPropertySource} 形式注入 Environment，与用户 YAML 配置等价</li>
 *   <li>同时维护 {@code spring.cloud.function.definition}，保证 Spring Cloud Stream 函数式模型可识别 binding</li>
 * </ul>
 *
 * <p>协作关系：
 * <ul>
 *   <li>处理 {@link StreamEventListener}：按 bindingName 派生 input/output binding，注册到 <code>spring.cloud.stream.bindings</code></li>
 *   <li>处理 {@link StreamRouteHandler}：按 eventType 派生 bindingName，进一步派生 destination 与 binding</li>
 *   <li>binding 命名遵循 Spring Cloud Stream 约定：<code>{bindingName}-in-0</code> / <code>{bindingName}-out-0</code></li>
 * </ul>
 *
 * <p>这样用户只需在方法上标注 @StreamEventListener，框架会自动完成绑定创建。
 *
 * @see StreamEventListener
 * @see StreamRouteHandler
 */
public class StreamBindingBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(StreamBindingBeanFactoryPostProcessor.class);

    /**
     * Spring Cloud Stream binding 配置前缀：<code>spring.cloud.stream.bindings</code>。
     */
    private static final String SPRING_BINDINGS_PREFIX = "spring.cloud.stream.bindings";
    /**
     * Spring Cloud Function definition 属性键：<code>spring.cloud.function.definition</code>。
     */
    private static final String FUNCTION_DEFINITION = "spring.cloud.function.definition";

    /**
     * 在 BeanFactory 准备阶段扫描注解并注入自动 binding 配置。
     *
     * <p>实现说明：
     * <ol>
     *   <li>读取 <code>structure.infra.stream.enabled</code>，未启用则直接返回</li>
     *   <li>读取全局默认值（default-group/default-content-type/default-binder/default-concurrency）</li>
     *   <li>迭代所有 BeanDefinition，扫描方法级与类级 {@link StreamEventListener}、方法级 {@link StreamRouteHandler}</li>
     *   <li>若用户未显式配置 <code>spring.cloud.function.definition</code>，自动拼接所有 bindingName</li>
     *   <li>将生成的属性以 <code>stream-auto-binding</code> 命名的 {@link MapPropertySource} 注入 Environment</li>
     * </ol>
     *
     * @param beanFactory 可配置的 BeanFactory
     * @throws BeansException 不会主动抛出
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        ConfigurableEnvironment environment = beanFactory.getBean(ConfigurableEnvironment.class);

        // 全局开关：未启用则跳过自动 binding 注册
        Boolean enabled = environment.getProperty("structure.infra.stream.enabled", Boolean.class, Boolean.TRUE);
        if (!enabled) {
            return;
        }

        // 读取全局默认值，用于 binding 字段兜底
        String defaultGroup = environment.getProperty("structure.infra.stream.default-group", "default");
        String defaultContentType = environment.getProperty("structure.infra.stream.default-content-type", "application/json");
        String defaultBinder = environment.getProperty("structure.infra.stream.default-binder");
        Integer defaultConcurrency = environment.getProperty("structure.infra.stream.default-concurrency", Integer.class, 1);

        Map<String, Object> properties = new LinkedHashMap<>();
        Set<String> functionDefinitions = new LinkedHashSet<>();

        // 扫描所有 BeanDefinition
        String[] beanNames = beanFactory.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            String beanClassName = beanDefinition.getBeanClassName();
            if (beanClassName == null) {
                continue;
            }

            Class<?> beanClass;
            try {
                beanClass = ClassUtils.forName(beanClassName, beanFactory.getBeanClassLoader());
            } catch (ClassNotFoundException e) {
                continue;
            }

            // 扫描方法上的 @StreamEventListener 注解
            for (Method method : beanClass.getDeclaredMethods()) {
                StreamEventListener annotation = AnnotatedElementUtils.findMergedAnnotation(method, StreamEventListener.class);
                if (annotation != null) {
                    processStreamEventListener(annotation, defaultGroup, defaultContentType, defaultBinder, defaultConcurrency, properties, functionDefinitions);
                }

                StreamRouteHandler routeAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, StreamRouteHandler.class);
                if (routeAnnotation != null) {
                    processStreamRouteHandler(routeAnnotation, defaultGroup, defaultContentType, defaultBinder, defaultConcurrency, properties, functionDefinitions);
                }

                
            }

            // 处理类级别的 @StreamEventListener 注解
            StreamEventListener classAnnotation = AnnotatedElementUtils.findMergedAnnotation(beanClass, StreamEventListener.class);
            if (classAnnotation != null) {
                processStreamEventListener(classAnnotation, defaultGroup, defaultContentType, defaultBinder, defaultConcurrency, properties, functionDefinitions);
            }
        }

        // 自动设置 spring.cloud.function.definition（如果未配置）
        String existingDefinition = environment.getProperty(FUNCTION_DEFINITION);
        if (!StringUtils.hasText(existingDefinition) && !functionDefinitions.isEmpty()) {
            properties.put(FUNCTION_DEFINITION, String.join(";", functionDefinitions));
            log.info("Auto set spring.cloud.function.definition: {}", String.join(";", functionDefinitions));
        }

        if (!properties.isEmpty()) {
            // 注入到 Environment 最前面，保证优先级高于其他源
            environment.getPropertySources().addFirst(
                    new MapPropertySource("stream-auto-binding", properties));
        }
    }

    /**
     * 处理单个 {@link StreamEventListener} 注解，派生 bindingName 并注册 input/output binding。
     *
     * <p>实现说明：
     * <ol>
     *   <li>bindingName 优先取 {@code bindingName()}，回退到 {@code value()}，均为空则跳过</li>
     *   <li>destination 优先取注解显式值，回退到由 bindingName 派生</li>
     *   <li>group/contentType 取注解显式值或全局默认值</li>
     *   <li>调用 {@link #registerBinding} 注册 input/output binding，并加入 functionDefinitions</li>
     * </ol>
     *
     * @param annotation           注解元数据
     * @param defaultGroup         全局默认 group
     * @param defaultContentType   全局默认 content-type
     * @param defaultBinder        全局默认 binder
     * @param defaultConcurrency   全局默认并发数
     * @param properties           待注入的属性 Map
     * @param functionDefinitions  待拼接的 function definition 集合
     */
    private void processStreamEventListener(StreamEventListener annotation, String defaultGroup,
                                            String defaultContentType, String defaultBinder, Integer defaultConcurrency,
                                            Map<String, Object> properties, Set<String> functionDefinitions) {
        String bindingName = annotation.bindingName();
        if (!StringUtils.hasText(bindingName)) {
            bindingName = annotation.value();
        }
        if (!StringUtils.hasText(bindingName)) {
            return;
        }

        String destination = StringUtils.hasText(annotation.destination())
                ? annotation.destination()
                : toDestination(bindingName);
        String group = StringUtils.hasText(annotation.group()) ? annotation.group() : defaultGroup;
        String contentType = StringUtils.hasText(annotation.contentType()) ? annotation.contentType() : defaultContentType;
        String binder = StringUtils.hasText(defaultBinder) ? defaultBinder : null;

        registerBinding(bindingName, destination, group, contentType, binder, defaultConcurrency, properties);
        functionDefinitions.add(bindingName);
    }

    /**
     * 处理单个 {@link StreamRouteHandler} 注解，按 eventType 派生 bindingName 与 destination 并注册 binding。
     *
     * <p>实现说明：路由场景下 bindingName 由 eventType 转换而来（替换 {@code .} / {@code _} 为 {@code -} 并小写）。
     *
     * @param annotation           注解元数据
     * @param defaultGroup         全局默认 group
     * @param defaultContentType   全局默认 content-type
     * @param defaultBinder        全局默认 binder
     * @param defaultConcurrency   全局默认并发数
     * @param properties           待注入的属性 Map
     * @param functionDefinitions  待拼接的 function definition 集合
     */
    private void processStreamRouteHandler(StreamRouteHandler annotation, String defaultGroup,
                                           String defaultContentType, String defaultBinder, Integer defaultConcurrency,
                                           Map<String, Object> properties, Set<String> functionDefinitions) {
        String eventType = annotation.eventType();
        if (!StringUtils.hasText(eventType)) {
            eventType = annotation.value();
        }
        if (!StringUtils.hasText(eventType)) {
            return;
        }

        // eventType 派生 bindingName 与 destination
        String bindingName = toBindingName(eventType);
        String destination = toDestination(eventType);

        registerBinding(bindingName, destination, defaultGroup, defaultContentType, defaultBinder, defaultConcurrency, properties);
        functionDefinitions.add(bindingName);
    }

    /**
     * 注册单个 binding 的 input/output 配置到 properties Map。
     *
     * <p>实现说明：遵循 Spring Cloud Stream 约定，每个 binding 同时生成
     * <code>{bindingName}-in-0</code> 与 <code>{bindingName}-out-0</code>，配置 destination/content-type/group/binder/concurrency。
     * 已注册过的 binding（按 input destination key 判断）会被跳过以保证幂等。
     *
     * @param bindingName    绑定名称
     * @param destination    目标 destination
     * @param group          消费者组
     * @param contentType    内容类型
     * @param binder         binder 名称，null 时跳过
     * @param concurrency    消费并发数，null 时跳过
     * @param properties     待注入的属性 Map
     */
    private void registerBinding(String bindingName, String destination, String group,
                                 String contentType, String binder, Integer concurrency,
                                 Map<String, Object> properties) {
        // Spring Cloud Stream 约定：input/output binding 名分别为 {bindingName}-in-0 / {bindingName}-out-0
        String inputBinding = bindingName + "-in-0";
        String outputBinding = bindingName + "-out-0";

        String inputDestKey = SPRING_BINDINGS_PREFIX + "." + inputBinding + ".destination";
        // 避免重复注册
        if (properties.containsKey(inputDestKey)) {
            return;
        }

        properties.put(inputDestKey, destination);
        properties.put(SPRING_BINDINGS_PREFIX + "." + outputBinding + ".destination", destination);
        properties.put(SPRING_BINDINGS_PREFIX + "." + inputBinding + ".content-type", contentType);
        properties.put(SPRING_BINDINGS_PREFIX + "." + outputBinding + ".content-type", contentType);

        if (StringUtils.hasText(group)) {
            properties.put(SPRING_BINDINGS_PREFIX + "." + inputBinding + ".group", group);
        }
        if (StringUtils.hasText(binder)) {
            properties.put(SPRING_BINDINGS_PREFIX + "." + inputBinding + ".binder", binder);
            properties.put(SPRING_BINDINGS_PREFIX + "." + outputBinding + ".binder", binder);
        }
        if (concurrency != null) {
            properties.put(SPRING_BINDINGS_PREFIX + "." + inputBinding + ".consumer.concurrency", concurrency);
        }

        log.info("Auto registered binding: {}, destination: {}, group: {}, contentType: {}",
                bindingName, destination, group, contentType);
    }

    /**
     * 将 eventType 转换为 bindingName，规则：将 {@code .} 与 {@code _} 替换为 {@code -} 并转小写。
     *
     * @param eventType 事件类型
     * @return 派生的 bindingName
     */
    private String toBindingName(String eventType) {
        return eventType.replace(".", "-").replace("_", "-").toLowerCase();
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

}
