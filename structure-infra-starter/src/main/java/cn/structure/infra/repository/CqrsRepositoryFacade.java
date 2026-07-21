package cn.structure.infra.repository;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import lombok.Getter;
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
 * @param <RD> 读委托类型（继承 IQueryDelegate）
 * @author chuck
 * @version 1.0.2
 * @since 2026/7/21
 */
@Getter
@Slf4j
public class CqrsRepositoryFacade<T, ID, D extends RepositoryDelegate<T, ID>, RD extends IQueryDelegate<T, ID>>
        extends RepositoryFacade<T, ID, D> {

    @Autowired(required = false)
    protected RD readDelegate;

    @Override
    public T queryById(ID id) {
        return executeReadOperation(
                () -> readDelegate.queryById(id),
                () -> super.queryById(id));
    }

    @Override
    public Optional<T> queryByIdOptional(ID id) {
        T entity = executeReadOperation(
                () -> readDelegate.queryById(id),
                () -> super.queryById(id));
        return Optional.ofNullable(entity);
    }

    @Override
    public T queryOne(T entity) {
        return executeReadOperation(
                () -> readDelegate.queryOne(entity),
                () -> super.queryOne(entity));
    }

    @Override
    public Optional<T> queryOneOptional(T entity) {
        return executeReadOperation(
                () -> readDelegate.queryOneOptional(entity),
                () -> super.queryOneOptional(entity));
    }

    @Override
    public List<T> queryList(T entity) {
        List<T> entityList = executeReadOperation(
                () -> readDelegate.queryList(entity),
                () -> super.queryList(entity));
        if (entityList == null || entityList.isEmpty()) {
            return List.of();
        }
        return entityList;
    }

    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        return executeReadOperation(
                () -> readDelegate.queryPage(reqPage),
                () -> super.queryPage(reqPage));
    }

    @Override
    public List<T> listByIds(List<ID> ids) {
        List<T> entityList = executeReadOperation(
                () -> readDelegate.listByIds(ids),
                () -> super.listByIds(ids));
        if (entityList == null || entityList.isEmpty()) {
            return List.of();
        }
        return entityList;
    }

    @Override
    public long count(T entity) {
        return executeReadOperation(
                () -> readDelegate.count(entity),
                () -> super.count(entity));
    }

    @Override
    public boolean exists(T entity) {
        return executeReadOperation(
                () -> readDelegate.exists(entity),
                () -> super.exists(entity));
    }

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

    @FunctionalInterface
    protected interface ReadOperation<R> {
        R execute();
    }
}
