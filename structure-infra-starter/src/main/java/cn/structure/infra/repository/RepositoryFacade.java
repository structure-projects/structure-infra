package cn.structure.infra.repository;

import cn.structure.common.repository.ICrudRepository;
import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structured.datascope.cache.manager.DataScopeCacheManager;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.Optional;
@Setter
public class RepositoryFacade<T, ID, P, D extends RepositoryDelegate<P, ID>> implements ICrudRepository<T, ID> {

    protected DataScopeCacheManager cacheManager;

    protected D baseDelegate;

    protected Class<T> entityClass;

    protected Class<P> poClass;

    public RepositoryFacade() {
    }

    public RepositoryFacade(Class<T> entityClass, Class<P> poClass) {
        this.entityClass = entityClass;
        this.poClass = poClass;
    }

    /**
     * 获取基础仓储代理
     *
     * @return 基础仓储代理
     */
    public D getBaseDelegate() {
        return baseDelegate;
    }

    @Override
    public T save(T entity) {
        P save = baseDelegate.save(toPo(entity));
        return toEntity(save);
    }

    @Override
    public void removeById(ID id) {
        baseDelegate.removeById(id);
    }

    @Override
    public T findById(ID id) {
        P po = baseDelegate.findById(id);
        return toEntity(po);
    }

    @Override
    public T queryById(ID id) {
        P po = baseDelegate.queryById(id);
        return toEntity(po);
    }

    @Override
    public Optional<T> queryByIdOptional(ID id) {
        P po = baseDelegate.queryById(id);
        return Optional.ofNullable(toEntity(po));
    }

    @Override
    public T queryOne(T entity) {
        P p = baseDelegate.queryOne(toPo(entity));
        return toEntity(p);
    }

    @Override
    public Optional<T> queryOneOptional(T entity) {
        Optional<P> p = baseDelegate.queryOneOptional(toPo(entity));
        return p.map(this::toEntity);
    }

    @Override
    public List<T> queryList(T entity) {
        List<P> poList = baseDelegate.queryList(toPo(entity));
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        ResPage<P> poPage = baseDelegate.queryPage(reqPage);
        if (poPage == null) {
            return null;
        }
        ResPage<T> tPage = new ResPage<>();
        tPage.setCurrent(poPage.getCurrent());
        tPage.setPages(poPage.getPages());
        tPage.setSize(poPage.getSize());
        tPage.setTotal(poPage.getTotal());
        if (poPage.getRecords() != null) {
            tPage.setRecords(poPage.getRecords().stream()
                    .map(this::toEntity)
                    .toList());
        }
        return tPage;
    }

    @Override
    public List<T> saveBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<P> poList = entities.stream()
                .map(this::toPo)
                .toList();
        List<P> savedPoList = baseDelegate.saveBatch(poList);
        return savedPoList.stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public void removeBatchByIds(List<ID> ids) {
        baseDelegate.removeBatchByIds(ids);
    }

    @Override
    public List<T> listByIds(List<ID> ids) {
        List<P> poList = baseDelegate.listByIds(ids);
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public long count(T entity) {
        return baseDelegate.count(toPo(entity));
    }

    @Override
    public boolean exists(T entity) {
        return baseDelegate.exists(toPo(entity));
    }

    protected T toEntity(P po) {
        if (po == null) {
            return null;
        }
        try {
            T entity = entityClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(po, entity);
            return entity;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert PO to entity", e);
        }
    }

    protected P toPo(T entity) {
        if (entity == null) {
            return null;
        }
        try {
            P po = poClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(entity, po);
            return po;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert entity to PO", e);
        }
    }
}
