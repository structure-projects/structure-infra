package cn.structure.infra.repository;

import cn.structure.common.repository.ICrudRepository;
import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

/**
 * 仓储门面
 * <p>
 * 作为领域层与持久化层之间的防腐层（ACL），提供统一的 CRUD 操作契约。
 * <p>
 * 单代理模式：维护单一的 RepositoryDelegate，所有操作（读和写）都通过该代理执行。
 * <p>
 * 注意：Entity ↔ PO 的转换逻辑已下移到 Delegate 实现层，
 * Facade 层只操作领域实体（Entity），不感知持久化对象（PO）。
 * <p>
 * 继承层次：
 * <pre>
 * RepositoryFacade&lt;T, ID&gt;                   // 基础门面（单代理模式）
 *     │
 *     └── CqrsRepositoryFacade&lt;T, ID, D, RD&gt; // CQRS门面（读写分离，两个代理）
 * </pre>
 *
 * @param <T>  领域实体类型
 * @param <ID> 主键类型
 * @author chuck
 * @version 1.0.4
 * @since 2026/6/28
 */
@Setter
@Getter
@Slf4j
public class RepositoryFacade<T, ID> implements ICrudRepository<T, ID> {

    @Autowired
    protected RepositoryDelegate<T, ID> delegate;

    protected Class<T> entityClass;

    public RepositoryFacade() {
    }

    /**
     * 保存实体（写操作，走 delegate）
     * <p>
     * 流程：Entity → delegate.save（内部转换为 PO）→ 返回 Entity
     *
     * @param entity 领域实体
     * @return 保存后的实体（包含可能生成的主键）
     */
    @Override
    public T save(T entity) {
        return delegate.save(entity);
    }

    /**
     * 根据主键删除（写操作，走 delegate）
     *
     * @param id 主键
     */
    @Override
    public void removeById(ID id) {
        delegate.removeById(id);
    }

    /**
     * 根据主键查询（写操作路径，走 delegate）
     *
     * @param id 主键
     * @return 实体对象，不存在时返回 null
     */
    @Override
    public T findById(ID id) {
        return delegate.findById(id);
    }

    /**
     * 根据主键查询（读操作，走 delegate）
     *
     * @param id 主键
     * @return 实体对象，不存在时返回 null
     */
    @Override
    public T queryById(ID id) {
        return delegate.queryById(id);
    }

    /**
     * 根据主键查询（Optional 包装，读操作，走 delegate）
     *
     * @param id 主键
     * @return Optional 包装的实体
     */
    @Override
    public Optional<T> queryByIdOptional(ID id) {
        T entity = delegate.queryById(id);
        return Optional.ofNullable(entity);
    }

    /**
     * 条件查询单条记录（读操作，走 delegate）
     *
     * @param entity 查询条件（非空属性作为等值条件）
     * @return 单条实体，不存在时返回 null
     */
    @Override
    public T queryOne(T entity) {
        return delegate.queryOne(entity);
    }

    /**
     * 条件查询单条记录（Optional 包装，读操作，走 delegate）
     *
     * @param entity 查询条件
     * @return Optional 包装的实体
     */
    @Override
    public Optional<T> queryOneOptional(T entity) {
        return delegate.queryOneOptional(entity);
    }

    /**
     * 条件查询列表（读操作，走 delegate）
     *
     * @param entity 查询条件，为 null 时查询全部
     * @return 实体列表，永远不为 null
     */
    @Override
    public List<T> queryList(T entity) {
        List<T> entityList = delegate.queryList(entity);
        if (entityList == null || entityList.isEmpty()) {
            return List.of();
        }
        return entityList;
    }

    /**
     * 分页查询（读操作，走 delegate）
     *
     * @param reqPage 分页参数
     * @return 分页结果
     */
    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        return delegate.queryPage(reqPage);
    }

    /**
     * 批量保存（写操作，走 delegate）
     *
     * @param entities 实体列表
     * @return 保存后的实体列表
     */
    @Override
    public List<T> saveBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return delegate.saveBatch(entities);
    }

    /**
     * 根据主键批量删除（写操作，走 delegate）
     *
     * @param ids 主键列表
     */
    @Override
    public void removeBatchByIds(List<ID> ids) {
        delegate.removeBatchByIds(ids);
    }

    /**
     * 根据主键列表批量查询（读操作，走 delegate）
     *
     * @param ids 主键列表
     * @return 实体列表，永远不为 null
     */
    @Override
    public List<T> listByIds(List<ID> ids) {
        List<T> entityList = delegate.listByIds(ids);
        if (entityList == null || entityList.isEmpty()) {
            return List.of();
        }
        return entityList;
    }

    /**
     * 统计数量（读操作，走 delegate）
     *
     * @param entity 查询条件
     * @return 记录数量
     */
    @Override
    public long count(T entity) {
        return delegate.count(entity);
    }

    /**
     * 判断是否存在（写操作路径，走 delegate）
     *
     * @param entity 查询条件
     * @return true 表示存在
     */
    @Override
    public boolean exists(T entity) {
        return delegate.exists(entity);
    }
}