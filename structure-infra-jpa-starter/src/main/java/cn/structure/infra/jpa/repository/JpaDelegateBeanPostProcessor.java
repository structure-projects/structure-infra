package cn.structure.infra.jpa.repository;

import cn.structure.infra.annotations.DelegateFor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * JPA RepositoryDelegate 的 BeanPostProcessor，负责为用户自定义 Delegate 子类自动注入 EntityManager 与实体类型。
 * <p>
 * 在仓储框架中，业务方可继承 {@link JpaRepositoryDelegate} 实现自定义 Delegate，并通过
 * {@link DelegateFor} 注解声明其服务的 PO 类型。本后处理器在 Bean 初始化完成后：
 * <ol>
 *   <li>识别所有 {@link JpaRepositoryDelegate} 类型的 Bean</li>
 *   <li>按"by name → by type → create"顺序解析并注入 {@link EntityManager}</li>
 *   <li>读取 {@link DelegateFor#po()} 指定的 PO 类型并通过 setter 注入</li>
 * </ol>
 * <p>
 * 与 {@link JpaDelegateFactory} 的分工：工厂负责"无自定义 Delegate 时自动创建"，
 * 本处理器负责"已有自定义 Delegate 时补齐依赖"，二者协同保证 RepositoryFacade 总能拿到可用的 Delegate。
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Slf4j
public class JpaDelegateBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    /** Spring 上下文，用于解析 EntityManager */
    private ApplicationContext applicationContext;

    /**
     * 注入 Spring 应用上下文，供后续按类型/名称查询 Bean。
     *
     * @param applicationContext Spring 应用上下文
     * @throws BeansException 上下文注入异常
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 在 Bean 初始化完成后，对自定义 JpaRepositoryDelegate 实现类注入 EntityManager 与实体类型。
     * <p>
     * 仅当 Bean 是 {@link JpaRepositoryDelegate} 实例时执行注入；EntityManager 解析失败仅告警不抛异常。
     *
     * @param bean     待处理的 Bean 实例
     * @param beanName Bean 名称
     * @return 原始 Bean（已注入依赖），未匹配类型时原样返回
     * @throws BeansException 处理过程中的异常
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof JpaRepositoryDelegate) {
            JpaRepositoryDelegate delegate = (JpaRepositoryDelegate) bean;

            // 解析 EntityManager：by name → by type → create
            EntityManager entityManager = getEntityManager();
            if (entityManager != null) {
                delegate.setEntityManager(entityManager);
                log.info("Injected EntityManager into JpaRepositoryDelegate: {}", beanName);
            } else {
                log.warn("No EntityManager available to inject into JpaRepositoryDelegate: {}", beanName);
            }

            // 读取 @DelegateFor 注解，识别该 Delegate 服务的 PO 类型并注入
            DelegateFor annotation = bean.getClass().getAnnotation(DelegateFor.class);
            if (annotation != null && annotation.po() != void.class) {
                delegate.setEntityClass(annotation.po());
                log.info("Injected entityClass {} into JpaRepositoryDelegate: {}", annotation.po().getSimpleName(), beanName);
            }
        }
        return bean;
    }

    /**
     * 解析 {@link EntityManager} 实例。
     * <p>
     * 解析顺序（按优先级）：
     * <ol>
     *   <li><b>by name</b>：从容器中获取名为 "entityManager" 的 Bean</li>
     *   <li><b>by type</b>：从 {@link EntityManagerFactory} 创建新的 EntityManager</li>
     *   <li>都失败时返回 null</li>
     * </ol>
     *
     * @return EntityManager 实例，无法解析时返回 null
     */
    private EntityManager getEntityManager() {
        // 1) by name：优先按 "entityManager" 名称获取已注册的容器 Bean
        try {
            Object bean = applicationContext.getBean("entityManager");
            if (bean instanceof EntityManager) {
                return (EntityManager) bean;
            }
        } catch (Exception e) {
            log.debug("entityManager bean not found by name");
        }

        // 2) by type → create：通过 EntityManagerFactory 创建新的 EntityManager
        try {
            EntityManagerFactory factory = applicationContext.getBean(EntityManagerFactory.class);
            if (factory != null) {
                return factory.createEntityManager();
            }
        } catch (Exception e) {
            log.debug("EntityManagerFactory bean not found");
        }

        return null;
    }
}