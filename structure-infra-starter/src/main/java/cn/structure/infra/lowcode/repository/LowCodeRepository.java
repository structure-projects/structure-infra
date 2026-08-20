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

package cn.structure.infra.lowcode.repository;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 低代码统一仓储接口
 * <p>
 * 低代码仓储体系的用户侧统一入口，方法名与 {@link cn.structure.common.repository.ICrudRepository}
 * 保持一致，仅在第一个参数增加资源名称以标识操作的资源。
 * <p>
 * 与传统泛型仓储的区别：
 * <ul>
 *   <li>无需定义实体类和 PO 类，通过资源名称和 Map 操作数据</li>
 *   <li>资源结构通过 DSL（配置文件或 API）动态定义</li>
 *   <li>框架根据配置自动路由到对应的存储引擎</li>
 *   <li>支持运行时动态注册新资源</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 保存数据
 * Map<String, Object> user = new HashMap<>();
 * user.put("username", "zhangsan");
 * user.put("email", "zhangsan@example.com");
 * lowCodeRepository.save("user", user);
 *
 * // 查询数据
 * Map<String, Object> found = lowCodeRepository.findById("user", 1L);
 *
 * // 分页查询
 * ReqPage reqPage = new ReqPage();
 * reqPage.setPage(1);
 * reqPage.setSize(10);
 * ResPage<Map<String, Object>> page = lowCodeRepository.queryPage("user", reqPage);
 * }</pre>
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
public interface LowCodeRepository {

    /**
     * 保存实体（新增或更新）
     * <p>
     * 如果数据中包含主键且主键对应的数据已存在，则执行更新；
     * 否则执行新增。
     *
     * @param resourceName 资源名称
     * @param data         数据 Map
     * @return 保存后的数据（包含自动生成的主键和自动填充字段）
     */
    Map<String, Object> save(String resourceName, Map<String, Object> data);

    /**
     * 根据 ID 删除
     *
     * @param resourceName 资源名称
     * @param id           主键值
     */
    void removeById(String resourceName, Object id);

    /**
     * 根据 ID 查询
     *
     * @param resourceName 资源名称
     * @param id           主键值
     * @return 数据 Map，不存在时返回 null
     */
    Map<String, Object> findById(String resourceName, Object id);

    /**
     * 根据 ID 查询（读操作，支持 CQRS 路由）
     *
     * @param resourceName 资源名称
     * @param id           主键值
     * @return 数据 Map，不存在时返回 null
     */
    Map<String, Object> queryById(String resourceName, Object id);

    /**
     * 根据 ID 查询（Optional 包装，读操作，支持 CQRS 路由）
     *
     * @param resourceName 资源名称
     * @param id           主键值
     * @return Optional 包装的数据，不存在时返回 Optional.empty()
     */
    Optional<Map<String, Object>> queryByIdOptional(String resourceName, Object id);

    /**
     * 条件查询单条记录（读操作，支持 CQRS 路由）
     *
     * @param resourceName 资源名称
     * @param queryParams  查询条件（非空字段作为等值条件）
     * @return 单条数据，不存在时返回 null
     */
    Map<String, Object> queryOne(String resourceName, Map<String, Object> queryParams);

    /**
     * 条件查询单条记录（Optional 包装，读操作，支持 CQRS 路由）
     *
     * @param resourceName 资源名称
     * @param queryParams  查询条件
     * @return Optional 包装的数据
     */
    Optional<Map<String, Object>> queryOneOptional(String resourceName, Map<String, Object> queryParams);

    /**
     * 条件查询列表（读操作，支持 CQRS 路由）
     *
     * @param resourceName 资源名称
     * @param queryParams  查询条件，为 null 时查询全部
     * @return 数据列表，永远不为 null
     */
    List<Map<String, Object>> queryList(String resourceName, Map<String, Object> queryParams);

    /**
     * 分页查询（读操作，支持 CQRS 路由）
     *
     * @param resourceName 资源名称
     * @param reqPage      分页参数
     * @return 分页结果
     */
    ResPage<Map<String, Object>> queryPage(String resourceName, ReqPage reqPage);

    /**
     * 批量保存
     *
     * @param resourceName 资源名称
     * @param dataList     数据列表
     * @return 保存后的数据列表
     */
    List<Map<String, Object>> saveBatch(String resourceName, List<Map<String, Object>> dataList);

    /**
     * 根据 ID 批量删除
     *
     * @param resourceName 资源名称
     * @param ids          主键列表
     */
    void removeBatchByIds(String resourceName, List<Object> ids);

    /**
     * 根据 ID 列表批量查询（读操作，支持 CQRS 路由）
     *
     * @param resourceName 资源名称
     * @param ids          主键列表
     * @return 数据列表，永远不为 null
     */
    List<Map<String, Object>> listByIds(String resourceName, List<Object> ids);

    /**
     * 统计数量（读操作，支持 CQRS 路由）
     *
     * @param resourceName 资源名称
     * @param queryParams  查询条件，为 null 时统计全部
     * @return 记录数量
     */
    long count(String resourceName, Map<String, Object> queryParams);

    /**
     * 判断是否存在（写操作，走基础存储）
     *
     * @param resourceName 资源名称
     * @param queryParams  查询条件
     * @return true 表示存在
     */
    boolean exists(String resourceName, Map<String, Object> queryParams);
}
