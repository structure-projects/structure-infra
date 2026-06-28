package cn.structure.infra.repository;

import cn.structure.common.repository.ICrudRepository;
import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structured.datascope.cache.manager.DataScopeCacheManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.Optional;

/**
 * 仓储门面
 * <p>
 * 作为领域层与持久化层之间的防腐层（ACL），提供统一的 CRUD 操作契约。
 * <p>
 * 支持两种代理：
 * - baseDelegate: RepositoryDelegate，承担写操作和默认读操作
 * - readDelegate: IQueryDelegate，承担读操作（CQRS 模式下使用）
 * <p>
 * 读操作回退机制：
 * - 如果配置了 readDelegate 且 cqrs=true，读操作优先使用 readDelegate
 * - 如果 readDelegate 执行失败（抛出异常），自动回退到 baseDelegate 执行
 * - baseDelegate 是最后的兜底
 *
 * @param <T>  领域实体类型
 * @param <ID> 主键类型
 * @param <P>  持久化对象类型（PO）
 * @param <D>  基础委托类型
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Setter
@Getter
@Slf4j
public class RepositoryFacade<T, ID, P, D extends RepositoryDelegate<P, ID>> implements ICrudRepository<T, ID> {

    protected DataScopeCacheManager cacheManager;

    /**
     * 基础代理
     * <p>
     * 承担所有写操作：save、removeById、saveBatch、removeBatchByIds
     * 同时作为读操作的默认代理和兜底代理
     */
    protected D baseDelegate;

    /**
     * 读代理（新增）
     * <p>
     * 承担所有读操作：findById、queryList、queryPage 等
     * 如果未配置或执行失败，读操作会回退到使用 baseDelegate
     * 类型为 IQueryDelegate，可以与 baseDelegate 类型不同
     */
    protected IQueryDelegate<P, ID> readDelegate;

    protected Class<T> entityClass;

    protected Class<P> poClass;

    public RepositoryFacade() {
    }

    public RepositoryFacade(Class<T> entityClass, Class<P> poClass) {
        this.entityClass = entityClass;
        this.poClass = poClass;
    }

    /**
     * 获取基础代理（原有方法保持不变）
     *
     * @return 基础代理
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
        P po = executeReadOperation(
                () -> readDelegate.queryById(id),
                () -> baseDelegate.queryById(id));
        return toEntity(po);
    }

    @Override
    public Optional<T> queryByIdOptional(ID id) {
        P po = executeReadOperation(
                () -> readDelegate.queryById(id),
                () -> baseDelegate.queryById(id));
        return Optional.ofNullable(toEntity(po));
    }

    @Override
    public T queryOne(T entity) {
        P p = executeReadOperation(
                () -> readDelegate.queryOne(toPo(entity)),
                () -> baseDelegate.queryOne(toPo(entity)));
        return toEntity(p);
    }

    @Override
    public Optional<T> queryOneOptional(T entity) {
        Optional<P> p = executeReadOperation(
                () -> readDelegate.queryOneOptional(toPo(entity)),
                () -> baseDelegate.queryOneOptional(toPo(entity)));
        return p.map(this::toEntity);
    }

    @Override
    public List<T> queryList(T entity) {
        List<P> poList = executeReadOperation(
                () -> readDelegate.queryList(toPo(entity)),
                () -> baseDelegate.queryList(toPo(entity)));
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        ResPage<P> poPage = executeReadOperation(
                () -> readDelegate.queryPage(reqPage),
                () -> baseDelegate.queryPage(reqPage));
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
        List<P> poList = executeReadOperation(
                () -> readDelegate.listByIds(ids),
                () -> baseDelegate.listByIds(ids));
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public long count(T entity) {
        return executeReadOperation(
                () -> readDelegate.count(toPo(entity)),
                () -> baseDelegate.count(toPo(entity)));
    }

    @Override
    public boolean exists(T entity) {
        return baseDelegate.exists(toPo(entity));
    }

    /**
     * 执行读操作，带回退机制（新增方法）
     * <p>
     * 优先使用 readDelegate 执行，如果 readDelegate 未配置或执行失败，
     * 自动回退到 baseDelegate 执行。
     *
     * @param readOperation     读代理操作
     * @param fallbackOperation 基础代理回退操作
     * @param <R>               返回类型
     * @return 操作结果
     */
    protected <R> R executeReadOperation(ReadOperation<R> readOperation, ReadOperation<R> fallbackOperation) {
        if (readDelegate != null) {
            try {
                return readOperation.execute();
            } catch (Exception e) {
                log.warn("Read delegate operation failed, falling back to base delegate: {}", e.getMessage());
            }
        }
        return fallbackOperation.execute();
    }

    /**
     * 读操作接口（新增接口）
     *
     * @param <R> 返回类型
     */
    @FunctionalInterface
    protected interface ReadOperation<R> {
        R execute();
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