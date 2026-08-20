/*
Copyright 2023 Structure Projects

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	`http://www.apache.org/licenses/LICENSE-2.0`

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package cn.structure.infra.repository;

import cn.structure.common.repository.ICrudRepository;

/**
 * 仓储委托接口
 * <p>
 * 定义持久化层的操作契约，面向领域实体（Entity）。
 * 不同的持久化技术（MyBatis、JPA、MongoDB等）提供各自的实现，
 * 每个实现负责内部的 Entity ↔ PO 转换。
 * <p>
 * 这是防腐层（ACL）的核心组件之一：
 * - 对外：由 RepositoryFacade 调用，面向领域模型，接收和返回领域实体
 * - 对内：操作持久化模型（PO），与具体存储技术交互，内部完成转换
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
 * @param <T>  领域实体类型（Entity）
 * @param <ID> 主键类型
 * @author chuck
 * @version 1.0.3
 * @since 2026/6/28
 */
public interface RepositoryDelegate<T, ID> extends ICrudRepository<T, ID>, IQueryDelegate<T, ID> {

    /**
     * 获取领域实体类型
     *
     * @return 领域实体类型
     */
    Class<T> getEntityClass();

    /**
     * 获取持久化对象类型（PO）
     *
     * @return 持久化对象类型
     */
    Class<?> getPoClass();

    /**
     * 获取主键类型
     *
     * @return 主键类型
     */
    Class<ID> getIdClass();

    /**
     * 获取 ID 字段名称
     * <p>
     * 通过 PO 类的 @Id 注解自动识别，默认为 "id"
     *
     * @return ID 字段名称
     */
    String getIdFieldName();

}