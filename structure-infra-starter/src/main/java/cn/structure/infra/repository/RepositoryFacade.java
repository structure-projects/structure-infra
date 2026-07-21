package cn.structure.infra.repository;

import cn.structure.common.repository.ICrudRepository;
import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@Getter
@Setter
@Slf4j
public class RepositoryFacade<T, ID, D extends RepositoryDelegate<T, ID>> implements ICrudRepository<T, ID> {

    protected D delegate;

    protected Class<T> entityClass;

    @Override
    public T save(T entity) {
        return delegate.save(entity);
    }

    @Override
    public void removeById(ID id) {
        delegate.removeById(id);
    }

    @Override
    public T findById(ID id) {
        return delegate.findById(id);
    }

    @Override
    public T queryById(ID id) {
        return delegate.queryById(id);
    }

    @Override
    public Optional<T> queryByIdOptional(ID id) {
        T entity = delegate.queryById(id);
        return Optional.ofNullable(entity);
    }

    @Override
    public T queryOne(T entity) {
        return delegate.queryOne(entity);
    }

    @Override
    public Optional<T> queryOneOptional(T entity) {
        return delegate.queryOneOptional(entity);
    }

    @Override
    public List<T> queryList(T entity) {
        List<T> entityList = delegate.queryList(entity);
        if (entityList == null || entityList.isEmpty()) {
            return List.of();
        }
        return entityList;
    }

    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        return delegate.queryPage(reqPage);
    }

    @Override
    public List<T> saveBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return delegate.saveBatch(entities);
    }

    @Override
    public void removeBatchByIds(List<ID> ids) {
        delegate.removeBatchByIds(ids);
    }

    @Override
    public List<T> listByIds(List<ID> ids) {
        List<T> entityList = delegate.listByIds(ids);
        if (entityList == null || entityList.isEmpty()) {
            return List.of();
        }
        return entityList;
    }

    @Override
    public long count(T entity) {
        return delegate.count(entity);
    }

    @Override
    public boolean exists(T entity) {
        return delegate.exists(entity);
    }
}
