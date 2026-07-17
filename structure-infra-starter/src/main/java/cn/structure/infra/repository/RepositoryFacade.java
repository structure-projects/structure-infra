package cn.structure.infra.repository;

import cn.structure.common.repository.ICrudRepository;
import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
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

    /**
     * 领域实体类型，用于 Entity ↔ PO 反射转换
     */
    protected Class<T> entityClass;

    /**
     * 持久化对象类型，用于 Entity ↔ PO 反射转换
     */
    protected Class<P> poClass;

    /**
     * 默认构造函数（用于无参实例化场景，需后续手动设置 entityClass/poClass）
     */
    public RepositoryFacade() {
    }

    /**
     * 根据实体类和 PO 类构造 RepositoryFacade
     *
     * @param entityClass 领域实体类型
     * @param poClass     持久化对象类型
     */
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

    /**
     * 保存实体（写操作，走 baseDelegate）
     * <p>
     * 流程：Entity → PO → baseDelegate.save → PO → Entity
     *
     * @param entity 领域实体
     * @return 保存后的实体（包含可能生成的主键）
     */
    @Override
    public T save(T entity) {
        // Entity → PO 转换后交给基础代理持久化，再回转为 Entity
        P save = baseDelegate.save(toPo(entity));
        return toEntity(save);
    }

    /**
     * 根据主键删除（写操作，走 baseDelegate）
     *
     * @param id 主键
     */
    @Override
    public void removeById(ID id) {
        baseDelegate.removeById(id);
    }

    /**
     * 根据主键查询（写操作路径，走 baseDelegate）
     * <p>
     * 此方法对应 ICrudRepository 契约，不参与 CQRS 路由，始终走基础代理。
     *
     * @param id 主键
     * @return 实体对象，不存在时返回 null
     */
    @Override
    public T findById(ID id) {
        P po = baseDelegate.findById(id);
        return toEntity(po);
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
        // 读操作：优先 readDelegate，失败回退 baseDelegate
        P po = executeReadOperation(
                () -> readDelegate.queryById(id),
                () -> baseDelegate.queryById(id));
        return toEntity(po);
    }

    /**
     * 根据主键查询（Optional 包装，读操作，支持 CQRS 路由）
     *
     * @param id 主键
     * @return Optional 包装的实体
     */
    @Override
    public Optional<T> queryByIdOptional(ID id) {
        P po = executeReadOperation(
                () -> readDelegate.queryById(id),
                () -> baseDelegate.queryById(id));
        return Optional.ofNullable(toEntity(po));
    }

    /**
     * 条件查询单条记录（读操作，支持 CQRS 路由）
     *
     * @param entity 查询条件（非空属性作为等值条件）
     * @return 单条实体，不存在时返回 null
     */
    @Override
    public T queryOne(T entity) {
        P p = executeReadOperation(
                () -> readDelegate.queryOne(toPo(entity)),
                () -> baseDelegate.queryOne(toPo(entity)));
        return toEntity(p);
    }

    /**
     * 条件查询单条记录（Optional 包装，读操作，支持 CQRS 路由）
     *
     * @param entity 查询条件
     * @return Optional 包装的实体
     */
    @Override
    public Optional<T> queryOneOptional(T entity) {
        Optional<P> p = executeReadOperation(
                () -> readDelegate.queryOneOptional(toPo(entity)),
                () -> baseDelegate.queryOneOptional(toPo(entity)));
        return p.map(this::toEntity);
    }

    /**
     * 条件查询列表（读操作，支持 CQRS 路由）
     *
     * @param entity 查询条件，为 null 时查询全部
     * @return 实体列表，永远不为 null
     */
    @Override
    public List<T> queryList(T entity) {
        List<P> poList = executeReadOperation(
                () -> readDelegate.queryList(toPo(entity)),
                () -> baseDelegate.queryList(toPo(entity)));
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        // PO 列表批量转换为 Entity 列表
        return poList.stream()
                .map(this::toEntity)
                .toList();
    }

    /**
     * 分页查询（读操作，支持 CQRS 路由）
     * <p>
     * 由于分页结果结构 {@link ResPage} 与实体类型绑定，需要逐项转换 PO → Entity。
     *
     * @param reqPage 分页参数
     * @return 分页结果，代理返回 null 时本方法也返回 null
     */
    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        ResPage<P> poPage = executeReadOperation(
                () -> readDelegate.queryPage(reqPage),
                () -> baseDelegate.queryPage(reqPage));
        if (poPage == null) {
            return null;
        }
        // 复制分页元数据，仅对 records 进行 PO → Entity 转换
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

    /**
     * 批量保存（写操作，走 baseDelegate）
     *
     * @param entities 实体列表
     * @return 保存后的实体列表
     */
    @Override
    public List<T> saveBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        // Entity 列表 → PO 列表，批量持久化后再回转
        List<P> poList = entities.stream()
                .map(this::toPo)
                .toList();
        List<P> savedPoList = baseDelegate.saveBatch(poList);
        return savedPoList.stream()
                .map(this::toEntity)
                .toList();
    }

    /**
     * 根据主键批量删除（写操作，走 baseDelegate）
     *
     * @param ids 主键列表
     */
    @Override
    public void removeBatchByIds(List<ID> ids) {
        baseDelegate.removeBatchByIds(ids);
    }

    /**
     * 根据主键列表批量查询（读操作，支持 CQRS 路由）
     *
     * @param ids 主键列表
     * @return 实体列表，永远不为 null
     */
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

    /**
     * 统计数量（读操作，支持 CQRS 路由）
     *
     * @param entity 查询条件
     * @return 记录数量
     */
    @Override
    public long count(T entity) {
        return executeReadOperation(
                () -> readDelegate.count(toPo(entity)),
                () -> baseDelegate.count(toPo(entity)));
    }

    /**
     * 判断是否存在（写操作路径，走 baseDelegate）
     *
     * @param entity 查询条件
     * @return true 表示存在
     */
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

    /**
     * PO → Entity 转换
     * <p>
     * 通过反射调用无参构造函数创建 Entity 实例，并使用 {@link BeanUtils#copyProperties(Object, Object)}
     * 复制同名属性。子类可重写以实现自定义映射逻辑。
     *
     * @param po 持久化对象，为 null 时返回 null
     * @return 领域实体
     * @throws RuntimeException 反射创建实例或属性复制失败时抛出
     */
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

    /**
     * Entity → PO 转换
     * <p>
     * 通过反射调用无参构造函数创建 PO 实例，并使用 {@link BeanUtils#copyProperties(Object, Object)}
     * 复制同名属性。子类可重写以实现自定义映射逻辑。
     *
     * @param entity 领域实体，为 null 时返回 null
     * @return 持久化对象
     * @throws RuntimeException 反射创建实例或属性复制失败时抛出
     */
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