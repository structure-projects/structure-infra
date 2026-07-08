package cn.structure.infra.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;

/**
 * RepositoryFacade 的 Spring {@link FactoryBean} 实现
 * <p>
 * 用于以编程方式注册 {@link RepositoryFacade} Bean，封装了创建过程所需的元数据
 * （entity 类型、PO 类型、Delegate 类型、{@link RepositoryDefinition}）。
 * <p>
 * 实际的 Delegate 注入不在此处完成，而是由 {@link RepositoryBeanPostProcessor}
 * 在容器刷新事件中统一处理。
 *
 * @param <T>  领域实体类型
 * @param <ID> 主键类型
 * @param <P>  持久化对象类型（PO）
 * @param <D>  基础委托类型
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Slf4j
public class RepositoryFacadeFactoryBean<T, ID, P, D extends RepositoryDelegate<P, ID>> implements FactoryBean<RepositoryFacade<T, ID, P, D>> {

    /**
     * 领域实体类型
     */
    private final Class<T> entityClass;

    /**
     * 持久化对象类型
     */
    private final Class<P> poClass;

    /**
     * 基础委托类型，用于在 BeanPostProcessor 中匹配 Delegate
     */
    private final Class<D> delegateClass;

    /**
     * 仓储定义元数据，封装 @Repository 注解的配置信息
     */
    private final RepositoryDefinition definition;

    /**
     * 构造 FactoryBean
     *
     * @param entityClass  领域实体类型
     * @param poClass      持久化对象类型
     * @param delegateClass 基础委托类型
     * @param definition   仓储定义元数据
     */
    public RepositoryFacadeFactoryBean(Class<T> entityClass, Class<P> poClass, Class<D> delegateClass, RepositoryDefinition definition) {
        this.entityClass = entityClass;
        this.poClass = poClass;
        this.delegateClass = delegateClass;
        this.definition = definition;
    }

    /**
     * 创建 RepositoryFacade 实例
     * <p>
     * 仅创建 Facade 本身并注入 entityClass/poClass，Delegate 的注入由
     * {@link RepositoryBeanPostProcessor#onApplicationEvent} 完成。
     *
     * @return RepositoryFacade 实例
     */
    @Override
    public RepositoryFacade<T, ID, P, D> getObject() {
        log.debug("Creating RepositoryFacade for entity: {}, po: {}, delegate: {}",
                entityClass.getName(), poClass.getName(), delegateClass.getName());
        return new RepositoryFacade<>(entityClass, poClass);
    }

    /**
     * 返回 FactoryBean 产出的对象类型
     *
     * @return RepositoryFacade 类型
     */
    @Override
    @SuppressWarnings("unchecked")
    public Class<?> getObjectType() {
        return RepositoryFacade.class;
    }

    /**
     * 声明为单例 Bean
     *
     * @return 始终返回 true
     */
    @Override
    public boolean isSingleton() {
        return true;
    }

    /**
     * 获取仓储定义元数据
     *
     * @return 仓储定义
     */
    public RepositoryDefinition getDefinition() {
        return definition;
    }
}