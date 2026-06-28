package cn.structure.infra.repository;

import cn.structure.common.repository.ICrudRepository;

/**
 * 仓储委托接口
 * <p>
 * 定义持久化层的操作契约，面向持久化对象（PO）。
 * 不同的持久化技术（MyBatis、JPA、MongoDB等）提供各自的实现。
 * <p>
 * 这是防腐层（ACL）的核心组件之一：
 * - 对外：由 RepositoryFacade 调用，面向领域模型
 * - 对内：操作持久化模型（PO），与具体存储技术交互
 * <p>
 * DDD 场景下，用户可以自定义实现此接口来满足特殊的持久化需求。
 * <p>
 * 继承关系：
 * <ul>
 *   <li>继承 {@link ICrudRepository}：提供完整 CRUD 能力（写+读）</li>
 *   <li>继承 {@link IQueryDelegate}：提供只读查询能力，
 *       使 RepositoryDelegate 可直接作为 CQRS 模式下的 READ 代理使用</li>
 * </ul>
 *
 * @param <T>  持久化对象类型（PO）
 * @param <ID> 主键类型
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
public interface RepositoryDelegate<T, ID> extends ICrudRepository<T, ID>, IQueryDelegate<T, ID> {

}