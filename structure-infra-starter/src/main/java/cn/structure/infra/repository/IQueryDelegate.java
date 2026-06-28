package cn.structure.infra.repository;

import cn.structure.common.repository.IQueryRepository;

import java.util.List;

/**
 * 只读仓储委托接口
 * <p>
 * 继承公共库的 IQueryRepository，表示读仓库的委托能力。
 * 用于 CQRS 模式下，读代理只需要实现读操作，不需要实现写操作。
 * <p>
 * 这是防腐层（ACL）的核心组件之一：
 * - 对外：由 RepositoryFacade 调用，面向领域模型
 * - 对内：操作持久化模型（PO），与具体存储技术交互
 * <p>
 * 与 RepositoryDelegate 的关系：
 * - RepositoryDelegate：继承 ICrudRepository + IQueryDelegate，包含完整的 CRUD 操作（写+读）
 * - IQueryDelegate：只继承 IQueryRepository + 补充方法，只有读操作
 * - RepositoryDelegate 是 IQueryDelegate 的子类型，因此所有 RepositoryDelegate 都可以作为读代理使用
 *
 * @param <T>  持久化对象类型（PO）
 * @param <ID> 主键类型
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
public interface IQueryDelegate<T, ID> extends IQueryRepository<T, ID> {

    /**
     * 根据主键查询
     *
     * @param id 主键
     * @return 实体对象
     */
    T findById(ID id);

    /**
     * 根据主键列表批量查询
     *
     * @param ids 主键列表
     * @return 实体列表
     */
    List<T> listByIds(List<ID> ids);

    /**
     * 统计数量
     *
     * @param entity 查询条件（非空属性作为条件）
     * @return 数量
     */
    long count(T entity);

    /**
     * 判断是否存在
     *
     * @param entity 查询条件（非空属性作为条件）
     * @return true 存在
     */
    boolean exists(T entity);
}