package cn.structure.infra.jpa.configuration;

import cn.structure.infra.jpa.repository.JpaDelegateBeanPostProcessor;
import cn.structure.infra.jpa.repository.JpaDelegateFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManager;

/**
 * JPA 自动配置类
 * <p>
 * 当检测到 JPA 相关依赖（{@link org.springframework.data.jpa.repository.JpaRepository}）时自动配置，
 * 注册 JPA 持久化所需的核心组件，使其与仓储框架无缝集成。
 * <p>
 * 注册的 Bean：
 * <ul>
 *   <li>{@link cn.structure.infra.jpa.repository.JpaDelegateFactory} - 仓储委托工厂，
 *       负责根据 PO 类创建 {@link cn.structure.infra.jpa.repository.JpaRepositoryDelegate} 实例，依赖 {@link jakarta.persistence.EntityManager}</li>
 *   <li>{@link cn.structure.infra.jpa.repository.JpaDelegateBeanPostProcessor} - Bean 后处理器，
 *       为自定义的 JpaRepositoryDelegate 实现类自动注入 EntityManager 和实体类</li>
 * </ul>
 * <p>
 * 工作机制：
 * <ol>
 *   <li>当 {@link cn.structure.infra.repository.RepositoryFacade} 需要获取 RepositoryDelegate 时，
 *       会通过 {@link cn.structure.infra.repository.RepositoryBeanPostProcessor} 查找匹配的 Delegate</li>
 *   <li>若未找到用户自定义的 Delegate，会通过 JpaDelegateFactory 自动创建</li>
 *   <li>DelegateBeanPostProcessor 确保用户自定义的 Delegate 实现能正确注入 EntityManager</li>
 * </ol>
 * <p>
 * 额外配置：
 * <ul>
 *   <li>{@link EnableJpaRepositories} - 启用 Spring Data JPA 仓库扫描</li>
 *   <li>{@link EnableTransactionManagement} - 启用事务管理</li>
 * </ul>
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
@EnableJpaRepositories
@EnableTransactionManagement
public class JpaAutoConfiguration {

    /**
     * 创建 JPA 仓储委托工厂
     * <p>
     * 负责根据 PO 类创建 JpaRepositoryDelegate 实例，通过 EntityManager 进行持久化操作。
     * 当 RepositoryFacade 需要获取 JPA 类型的 RepositoryDelegate 时，会通过此工厂进行创建。
     *
     * @param entityManager JPA 实体管理器，用于执行数据库操作
     * @return JpaDelegateFactory 实例
     */
    @Bean
    @ConditionalOnBean(EntityManager.class)
    public JpaDelegateFactory jpaDelegateFactory(EntityManager entityManager) {
        return new JpaDelegateFactory(entityManager);
    }

    /**
     * 创建 JPA 委托 Bean 后处理器
     * <p>
     * 在 Bean 初始化完成后，自动为 JpaRepositoryDelegate 实现类注入 EntityManager 和实体类。
     * <p>
     * 处理逻辑：
     * 1. 扫描所有 Bean，筛选出 JpaRepositoryDelegate 的实例
     * 2. 从 Spring 上下文获取 EntityManager 并注入到 Delegate 实例中
     * 3. 检查是否存在 {@link cn.structure.infra.annotations.DelegateFor} 注解
     * 4. 将注解中指定的 PO 类设置到 Delegate 实例中
     *
     * @return JpaDelegateBeanPostProcessor 实例
     */
    @Bean
    @ConditionalOnClass(name = "jakarta.persistence.EntityManager")
    public JpaDelegateBeanPostProcessor jpaDelegateBeanPostProcessor() {
        return new JpaDelegateBeanPostProcessor();
    }
}
