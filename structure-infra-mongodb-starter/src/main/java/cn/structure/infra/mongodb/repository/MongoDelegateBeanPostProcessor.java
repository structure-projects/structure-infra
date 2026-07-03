package cn.structure.infra.mongodb.repository;

import cn.structure.infra.annotations.DelegateFor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * MongoDB RepositoryDelegate 的 BeanPostProcessor，负责为用户自定义 Delegate 子类自动注入 MongoTemplate 与实体类型。
 * <p>
 * 在仓储框架中，业务方可继承 {@link MongoRepositoryDelegate} 实现自定义 Delegate，并通过
 * {@link DelegateFor} 注解声明其服务的 PO 类型。本后处理器在 Bean 初始化完成后：
 * <ol>
 *   <li>识别所有 {@link MongoRepositoryDelegate} 类型的 Bean</li>
 *   <li>按类型从容器获取 {@link MongoTemplate} 并注入</li>
 *   <li>读取 {@link DelegateFor#po()} 指定的 PO 类型并通过 setter 注入</li>
 * </ol>
 * <p>
 * 与 {@link MongoDelegateFactory} 的分工：工厂负责"无自定义 Delegate 时自动创建"，
 * 本处理器负责"已有自定义 Delegate 时补齐依赖"，二者协同保证 RepositoryFacade 总能拿到可用的 Delegate。
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Slf4j
public class MongoDelegateBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    /** Spring 上下文，用于按类型获取 MongoTemplate */
    private ApplicationContext applicationContext;

    /**
     * 注入 Spring 应用上下文，供后续按类型查询 Bean。
     *
     * @param applicationContext Spring 应用上下文
     * @throws BeansException 上下文注入异常
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 在 Bean 初始化完成后，对自定义 MongoRepositoryDelegate 实现类注入 MongoTemplate 与实体类型。
     * <p>
     * 仅当 Bean 是 {@link MongoRepositoryDelegate} 实例时执行注入；MongoTemplate 解析失败仅告警不抛异常。
     *
     * @param bean     待处理的 Bean 实例
     * @param beanName Bean 名称
     * @return 原始 Bean（已注入依赖），未匹配类型时原样返回
     * @throws BeansException 处理过程中的异常
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof MongoRepositoryDelegate) {
            MongoRepositoryDelegate delegate = (MongoRepositoryDelegate) bean;
            try {
                // 按类型从容器获取 MongoTemplate 并注入
                MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
                delegate.setMongoTemplate(mongoTemplate);

                // 读取 @DelegateFor 注解，识别该 Delegate 服务的 PO 类型并注入
                DelegateFor annotation = bean.getClass().getAnnotation(DelegateFor.class);
                if (annotation != null && annotation.po() != void.class) {
                    delegate.setEntityClass(annotation.po());
                }

                log.info("Injected MongoTemplate into MongoRepositoryDelegate: {}", beanName);
            } catch (Exception e) {
                log.warn("Failed to inject MongoTemplate into MongoRepositoryDelegate {}: {}", beanName, e.getMessage());
            }
        }
        return bean;
    }
}