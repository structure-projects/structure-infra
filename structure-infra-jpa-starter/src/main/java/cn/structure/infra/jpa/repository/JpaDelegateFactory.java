package cn.structure.infra.jpa.repository;

import cn.structure.infra.repository.RepositoryDelegate;
import cn.structure.infra.repository.RepositoryDelegateFactory;
import cn.structure.infra.repository.RepositoryType;

import jakarta.persistence.EntityManager;

/**
 * JPA 仓储委托工厂
 * <p>
 * 自动创建 JpaRepositoryDelegate 实例
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
public class JpaDelegateFactory implements RepositoryDelegateFactory {

    private final EntityManager entityManager;

    public JpaDelegateFactory(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public RepositoryType getType() {
        return RepositoryType.JPA;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public RepositoryDelegate<?, ?> createDelegate(Class<?> poClass, Class<?> idClass) {
        try {
            return new JpaRepositoryDelegate(entityManager, poClass);
        } catch (Exception e) {
            return null;
        }
    }
}
