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
 * 这样用户只需在方法上标注 @StreamEventListener，框架会自动完成绑定创建。
 */
public class StreamBindingBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(StreamBindingBeanFactoryPostProcessor.class);

    private static final String SPRING_BINDINGS_PREFIX = "spring.cloud.stream.bindings";
    private static final String FUNCTION_DEFINITION = "spring.cloud.function.definition";

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        ConfigurableEnvironment environment = beanFactory.getBean(ConfigurableEnvironment.class);

        Boolean enabled = environment.getProperty("structure.infra.stream.enabled", Boolean.class, Boolean.TRUE);
        if (!enabled) {
            return;
        }

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
            environment.getPropertySources().addFirst(
                    new MapPropertySource("stream-auto-binding", properties));
        }
    }

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

        String bindingName = toBindingName(eventType);
        String destination = toDestination(eventType);

        registerBinding(bindingName, destination, defaultGroup, defaultContentType, defaultBinder, defaultConcurrency, properties);
        functionDefinitions.add(bindingName);
    }

    private void registerBinding(String bindingName, String destination, String group,
                                 String contentType, String binder, Integer concurrency,
                                 Map<String, Object> properties) {
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

    private String toBindingName(String eventType) {
        return eventType.replace(".", "-").replace("_", "-").toLowerCase();
    }

    private String toDestination(String name) {
        return name.replace(".", "-").replace("_", "-").toLowerCase() + "-exchange";
    }

}