package cn.structure.infra.lowcode.repository;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 低代码存储操作接口
 * <p>
 * 低代码仓储体系的内部操作接口，定义具体存储引擎需要实现的操作契约。
 * 与 {@link LowCodeRepository} 的区别是缺少 resourceName 参数，
 * 因为每个 LowCodeStorage 实例只对应一个资源。
 * <p>
 * 各存储引擎（MySQL、MongoDB、Elasticsearch 等）通过实现此接口
 * 提供具体的存储操作，由 {@link LowCodeRepoFactory} 负责创建实例。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
public interface LowCodeStorage {

    /**
     * 初始化存储（建表/建集合/建索引等）
     * <p>
     * 在资源注册时调用，确保存储容器存在。如果已存在则跳过。
     */
    void initialize();

    /**
     * 保存数据（新增或更新）
     *
     * @param data 数据 Map
     * @return 保存后的数据
     */
    Map<String, Object> save(Map<String, Object> data);

    /**
     * 根据 ID 删除
     *
     * @param id 主键值
     */
    void removeById(Object id);

    /**
     * 根据 ID 查询（写操作路径，走基础存储）
     *
     * @param id 主键值
     * @return 数据 Map
     */
    Map<String, Object> findById(Object id);

    /**
     * 根据 ID 查询（读操作路径，可走读存储）
     *
     * @param id 主键值
     * @return 数据 Map
     */
    Map<String, Object> queryById(Object id);

    /**
     * 条件查询单条
     *
     * @param queryParams 查询条件
     * @return 单条数据
     */
    Map<String, Object> queryOne(Map<String, Object> queryParams);

    /**
     * 条件查询单条（Optional 包装）
     *
     * @param queryParams 查询条件
     * @return Optional 包装的数据
     */
    Optional<Map<String, Object>> queryOneOptional(Map<String, Object> queryParams);

    /**
     * 条件查询列表
     *
     * @param queryParams 查询条件
     * @return 数据列表
     */
    List<Map<String, Object>> queryList(Map<String, Object> queryParams);

    /**
     * 分页查询
     *
     * @param reqPage 分页参数
     * @return 分页结果
     */
    ResPage<Map<String, Object>> queryPage(ReqPage reqPage);

    /**
     * 批量保存
     *
     * @param dataList 数据列表
     * @return 保存后的数据列表
     */
    List<Map<String, Object>> saveBatch(List<Map<String, Object>> dataList);

    /**
     * 根据 ID 批量删除
     *
     * @param ids 主键列表
     */
    void removeBatchByIds(List<Object> ids);

    /**
     * 根据 ID 列表批量查询
     *
     * @param ids 主键列表
     * @return 数据列表
     */
    List<Map<String, Object>> listByIds(List<Object> ids);

    /**
     * 统计数量
     *
     * @param queryParams 查询条件
     * @return 记录数量
     */
    long count(Map<String, Object> queryParams);

    /**
     * 判断是否存在
     *
     * @param queryParams 查询条件
     * @return true 表示存在
     */
    boolean exists(Map<String, Object> queryParams);
}
