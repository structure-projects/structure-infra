package cn.structure.infra.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;

@Slf4j
public class RepositoryFacadeFactoryBean<T, ID, P, D extends RepositoryDelegate<P, ID>> implements FactoryBean<RepositoryFacade<T, ID, P, D>> {

    private final Class<T> entityClass;
    private final Class<P> poClass;
    private final Class<D> delegateClass;
    private final RepositoryDefinition definition;

    public RepositoryFacadeFactoryBean(Class<T> entityClass, Class<P> poClass, Class<D> delegateClass, RepositoryDefinition definition) {
        this.entityClass = entityClass;
        this.poClass = poClass;
        this.delegateClass = delegateClass;
        this.definition = definition;
    }

    @Override
    public RepositoryFacade<T, ID, P, D> getObject() {
        log.debug("Creating RepositoryFacade for entity: {}, po: {}, delegate: {}",
                entityClass.getName(), poClass.getName(), delegateClass.getName());
        return new RepositoryFacade<>(entityClass, poClass);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<?> getObjectType() {
        return RepositoryFacade.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public RepositoryDefinition getDefinition() {
        return definition;
    }
}