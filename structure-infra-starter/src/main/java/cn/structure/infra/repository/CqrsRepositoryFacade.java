package cn.structure.infra.repository;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.annotations.ReadDelegate;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

/**
 * CQRS 仓储门面
 * <p>
 * 支持 CQRS 读写分离模式的仓储门面实现，继承自 RepositoryFacade。
 * <p>
 * 维护两个代理：
 * - baseDelegate: RepositoryDelegate，承担写操作和默认读操作
 * - readDelegate: IQueryDelegate，承担读操作（CQRS 模式下使用）
 * <p>
 * 读操作回退机制：
 * - 如果配置了 readDelegate，读操作优先使用 readDelegate
 * - 如果 readDelegate 执行失败（抛出异常），自动回退到 baseDelegate 执行
 * - baseDelegate 是最后的兜底
 * <p>
 * 继承层次：
 * <pre>
 * RepositoryFacade&lt;T, ID&gt;                   // 基础门面（单代理模式）
 *     │
 *     └── CqrsRepositoryFacade&lt;T, ID, D, RD&gt; // CQRS门面（读写分离，两个代理）
 * </pre>
 * <p>
 * 职责划分：
 * <ul>
 *   <li>维护基础代理（baseDelegate）和读代理（readDelegate）</li>
 *   <li>写操作始终走 baseDelegate（通过父类 delegate 字段）</li>
 *   <li>读操作优先走 readDelegate，失败回退到 baseDelegate</li>
 *   <li>提供统一的读操作执行框架（executeReadOperation）</li>
 * </ul>
 *
 * @param <T>  领域实体类型
 * @param <ID> 主键类型
 * @param <D>  基础委托类型（继承 RepositoryDelegate）
 * @param <RD> 读委托类型（继承 IQueryDelegate）
 * @author chuck
 * @version 1.0.2
 * @since 2026/7/21
 */
@Setter
@Getter
@Slf4j
public class CqrsRepositoryFacade<T, ID, D extends RepositoryDelegate<T, ID>, RD extends IQueryDelegate<T, ID>>
        extends RepositoryFacade<T, ID> {

    @Autowired
    protected D baseDelegate;

    @Autowired
    @ReadDelegate
    protected RD readDelegate;

    public CqrsRepositoryFacade() {
        super();
    }

    public D getBaseDelegate() {
        return baseDelegate;
    }

    public void setBaseDelegate(D baseDelegate) {
        this.baseDelegate = baseDelegate;
        super.setDelegate(baseDelegate);
    }

    /**
     * 根据主键查询（读操作，支持 CQRS 路由）
     * <p>
     * 优先走 readDelegate，失败回退到 baseDelegate
     *
     * @param id 主键
     * @return 实体对象，不存在时返回 null
     */
    @Override
    public T queryById(ID id) {
        return executeReadOperation(
                () -> readDelegate.queryById(id),
                () -> baseDelegate.queryById(id));
    }

    /**
     * 根据主键查询（Optional 包装，读操作，支持 CQRS 路由）
     *
     * @param id 主键
     * @return Optional 包装的实体
     */
    @Override
    public Optional<T> queryByIdOptional(ID id) {
        T entity = executeReadOperation(
                () -> readDelegate.queryById(id),
                () -> baseDelegate.queryById(id));
        return Optional.ofNullable(entity);
    }

    /**
     * 条件查询单条记录（读操作，支持 CQRS 路由）
     *
     * @param entity 查询条件（非空属性作为等值条件）
     * @return 单条实体，不存在时返回 null
     */
    @Override
    public T queryOne(T entity) {
        return executeReadOperation(
                () -> readDelegate.queryOne(entity),
                () -> baseDelegate.queryOne(entity));
    }

    /**
     * 条件查询单条记录（Optional 包装，读操作，支持 CQRS 路由）
     *
     * @param entity 查询条件
     * @return Optional 包装的实体
     */
    @Override
    public Optional<T> queryOneOptional(T entity) {
        return executeReadOperation(
                () -> readDelegate.queryOneOptional(entity),
                () -> baseDelegate.queryOneOptional(entity));
    }

    /**
     * 条件查询列表（读操作，支持 CQRS 路由）
     *
     * @param entity 查询条件，为 null 时查询全部
     * @return 实体列表，永远不为 null
     */
    @Override
    public List<T> queryList(T entity) {
        List<T> entityList = executeReadOperation(
                () -> readDelegate.queryList(entity),
                () -> baseDelegate.queryList(entity));
        if (entityList == null || entityList.isEmpty()) {
            return List.of();
        }
        return entityList;
    }

    /**
     * 分页查询（读操作，支持 CQRS 路由）
     *
     * @param reqPage 分页参数
     * @return 分页结果
     */
    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        return executeReadOperation(
                () -> readDelegate.queryPage(reqPage),
                () -> baseDelegate.queryPage(reqPage));
    }

    /**
     * 根据主键列表批量查询（读操作，支持 CQRS 路由）
     *
     * @param ids 主键列表
     * @return 实体列表，永远不为 null
     */
    @Override
    public List<T> listByIds(List<ID> ids) {
        List<T> entityList = executeReadOperation(
                () -> readDelegate.listByIds(ids),
                () -> baseDelegate.listByIds(ids));
        if (entityList == null || entityList.isEmpty()) {
            return List.of();
        }
        return entityList;
    }

    /**
     * 统计数量（读操作，支持 CQRS 路由）
     *
     * @param entity 查询条件
     * @return 记录数量
     */
    @Override
    public long count(T entity) {
        return executeReadOperation(
                () -> readDelegate.count(entity),
                () -> baseDelegate.count(entity));
    }

    /**
     * 执行读操作，带回退机制
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
     * 读操作接口
     *
     * @param <R> 返回类型
     */
    @FunctionalInterface
    protected interface ReadOperation<R> {
        R execute();
    }
}