package cn.structure.infra.jpa.repository;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.repository.RepositoryDelegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 基于 JPA 的 RepositoryDelegate 适配实现
 * <p>
 * 该类是 RepositoryDelegate SPI 在 JPA 存储类型下的标准实现，通过 {@link EntityManager}
 * 的 Criteria API 完成实体 CRUD。它在仓储框架中扮演"具体存储适配层"的角色：
 * <ul>
 *   <li>上层由 {@code RepositoryFacade} 统一暴露给业务方，本类不直接面向业务</li>
 *   <li>当用户未提供自定义 Delegate 时，由 {@link JpaDelegateFactory} 自动创建本类实例</li>
 *   <li>当用户提供自定义 Delegate 子类时，由 {@link JpaDelegateBeanPostProcessor}
 *       在 Bean 初始化后自动注入 EntityManager 与实体类型</li>
 * </ul>
 * <p>
 * 实现说明：
 * <ul>
 *   <li>使用 Jakarta 命名空间（{@code jakarta.persistence.*}），适用于 Spring Boot 3.x</li>
 *   <li>查询条件通过反射读取实体非空字段，组装为 Criteria API 的 {@link Predicate}（等值匹配）</li>
 *   <li>save 委托给 {@link EntityManager#merge(Object)}，由 JPA 自动判断新增或更新</li>
 *   <li>分页采用"内存分页"：先 findAll 取全量再切片，适用于中小数据量；
 *       大数据量场景建议用户自定义 Delegate 子类覆盖 queryPage 使用原生 SQL 分页</li>
 * </ul>
 *
 * @param <T>  实体（PO）类型
 * @param <ID> 主键类型
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Slf4j
public class JpaRepositoryDelegate<T, ID> implements RepositoryDelegate<T, ID> {

    /** JPA 实体管理器，承担实际持久化操作 */
    protected EntityManager entityManager;
    /** PO 实体类型，用于 Criteria API 与 find */
    protected Class<T> entityClass;

    /**
     * 默认构造器，用于用户自定义子类场景。
     * <p>
     * 创建后由 {@link JpaDelegateBeanPostProcessor} 通过 setter 注入依赖。
     */
    public JpaRepositoryDelegate() {
    }

    /**
     * 全参构造器，工厂自动创建场景使用。
     *
     * @param entityManager JPA 实体管理器
     * @param entityClass   PO 实体类型
     */
    public JpaRepositoryDelegate(EntityManager entityManager, Class<T> entityClass) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
        log.info("JpaRepositoryDelegate initialized for entity: {}", entityClass.getSimpleName());
    }

    /**
     * 注入 EntityManager，供 BeanPostProcessor 在自定义子类上调用。
     *
     * @param entityManager JPA 实体管理器
     */
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * 注入 PO 实体类型，供 BeanPostProcessor 在自定义子类上调用。
     *
     * @param entityClass PO 实体类型
     */
    public void setEntityClass(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 保存或更新实体。
     * <p>
     * 委托给 {@link EntityManager#merge(Object)}，由 JPA 根据实体主键自动判断新增或更新。
     *
     * @param entity 实体对象，为 null 或依赖未就绪时返回 null
     * @return merge 后的实体实例（可能是新对象引用）
     */
    @Override
    public T save(T entity) {
        if (entity == null || entityManager == null || entityClass == null) {
            return null;
        }
        T saved = entityManager.merge(entity);
        log.debug("Saved entity: {}", saved);
        return saved;
    }

    /**
     * 根据主键删除记录。
     * <p>
     * JPA 删除前必须先 find 出受管实体再 remove，无法直接按 ID 删除。
     *
     * @param id 主键值，为 null 时不执行任何操作
     */
    @Override
    public void removeById(ID id) {
        if (id != null) {
            // JPA 删除需先加载受管实体再 remove
            T entity = findById(id);
            if (entity != null) {
                entityManager.remove(entity);
                log.debug("Removed entity: id={}", id);
            }
        }
    }

    /**
     * 根据主键查询实体。
     *
     * @param id 主键值，为 null 时返回 null
     * @return 实体对象，未找到时返回 null
     */
    @Override
    public T findById(ID id) {
        if (id == null) {
            return null;
        }
        T entity = entityManager.find(entityClass, id);
        log.debug("Find by id: id={}, found={}", id, entity != null);
        return entity;
    }

    /**
     * 根据主键查询（与 findById 等价，语义上用于"读模型"）。
     *
     * @param id 主键值
     * @return 实体对象，未找到时返回 null
     */
    @Override
    public T queryById(ID id) {
        return findById(id);
    }

    /**
     * 根据主键查询并以 {@link Optional} 包装返回。
     *
     * @param id 主键值
     * @return 包含实体的 Optional，未找到时为 {@link Optional#empty()}
     */
    @Override
    public Optional<T> queryByIdOptional(ID id) {
        return Optional.ofNullable(findById(id));
    }

    /**
     * 根据非空字段等值匹配查询单条记录。
     * <p>
     * 通过 Criteria API 构建等值条件，取结果集首条；多于一条时仅返回首条。
     *
     * @param condition 查询条件对象，为 null 时返回 null
     * @return 首条匹配记录，无匹配时返回 null
     */
    @Override
    public T queryOne(T condition) {
        if (condition == null) {
            return null;
        }
        List<T> results = queryList(condition);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 根据条件查询单条记录，并以 {@link Optional} 包装返回。
     *
     * @param condition 查询条件对象
     * @return 包含首条匹配记录的 Optional
     */
    @Override
    public Optional<T> queryOneOptional(T condition) {
        return Optional.ofNullable(queryOne(condition));
    }

    /**
     * 根据条件查询列表。
     * <p>
     * 条件为 null 时查询全部；否则按非空字段构建 Criteria 等值条件。
     *
     * @param condition 查询条件对象，可为 null
     * @return 匹配的实体列表，无匹配时返回空列表
     */
    @Override
    public List<T> queryList(T condition) {
        if (condition == null) {
            return findAll();
        }
        return queryByCondition(condition);
    }

    /**
     * 分页查询（内存分页）。
     * <p>
     * <b>注意：JPA 不支持原生分页时使用内存分页</b>——先 findAll 取全量结果，
     * 再按 subList 切片返回当前页。该实现适用于中小数据量；大数据量场景
     * 建议用户自定义 Delegate 子类覆盖本方法，使用原生 SQL 分页。
     *
     * @param reqPage 分页请求（页码从 1 开始、每页大小，为 null 时取默认 1/10）
     * @return 分页结果，含当前页、总页数、总条数、当前页记录
     */
    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        // JPA 页码从 0 开始，业务页码从 1 开始，需减 1 转换
        int pageNum = reqPage.getPage() != null ? reqPage.getPage() - 1 : 0;
        int pageSize = reqPage.getSize() != null ? reqPage.getSize() : 10;

        // 内存分页：先取全量再切片（大数据量场景应覆盖此方法）
        List<T> allResults = findAll();
        int start = pageNum * pageSize;
        int end = Math.min(start + pageSize, allResults.size());

        List<T> pageContent = start < allResults.size() ? allResults.subList(start, end) : List.of();

        ResPage<T> resPage = new ResPage<>();
        // 返回业务侧时页码再加回 1
        resPage.setCurrent((long) (pageNum + 1));
        // 总页数向上取整
        resPage.setPages((long) ((allResults.size() + pageSize - 1) / pageSize));
        resPage.setSize((long) pageSize);
        resPage.setTotal((long) allResults.size());
        resPage.setRecords(pageContent);

        log.debug("Query page: page={}, size={}, total={}, records={}",
                pageNum + 1, pageSize, allResults.size(), pageContent.size());
        return resPage;
    }

    /**
     * 通过 Criteria API 查询全部实体。
     *
     * @return 全部实体列表
     */
    private List<T> findAll() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        query.from(entityClass);
        return entityManager.createQuery(query).getResultList();
    }

    /**
     * 通过 Criteria API 按条件等值查询。
     *
     * @param condition 条件对象
     * @return 匹配的实体列表
     */
    private List<T> queryByCondition(T condition) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        Root<T> root = query.from(entityClass);

        // 构建等值 Predicate 数组并拼接到 WHERE 子句
        Predicate[] predicates = buildPredicates(cb, root, condition);
        if (predicates.length > 0) {
            query.where(predicates);
        }

        return entityManager.createQuery(query).getResultList();
    }

    /**
     * 反射读取条件对象非空字段，构建等值 {@link Predicate} 数组。
     *
     * @param cb        CriteriaBuilder
     * @param root       查询根
     * @param condition 条件对象
     * @return 等值 Predicate 数组
     */
    private Predicate[] buildPredicates(CriteriaBuilder cb, Root<T> root, T condition) {
        List<Predicate> predicates = new java.util.ArrayList<>();
        try {
            Field[] fields = getAllFields(condition.getClass());
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(condition);
                if (value != null) {
                    // 直接以字段名作为属性路径，等值匹配
                    predicates.add(cb.equal(root.get(field.getName()), value));
                }
            }
        } catch (Exception e) {
            log.warn("Error building predicates: {}", e.getMessage());
        }
        return predicates.toArray(new Predicate[0]);
    }

    /**
     * 收集类及其所有父类（直到 Object）的声明字段。
     *
     * @param clazz 起始类
     * @return 全部字段数组
     */
    private Field[] getAllFields(Class<?> clazz) {
        List<Field> fields = new java.util.ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields.toArray(new Field[0]);
    }

    /**
     * 批量保存实体（逐条 merge）。
     *
     * @param entities 实体列表，为 null 或空时返回空列表
     * @return merge 后的实体列表
     */
    @Override
    public List<T> saveBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(entityManager::merge)
                .toList();
    }

    /**
     * 根据主键列表批量删除（逐条 find + remove）。
     *
     * @param ids 主键列表，为 null 时不执行任何操作
     */
    @Override
    public void removeBatchByIds(List<ID> ids) {
        if (ids != null) {
            ids.forEach(this::removeById);
        }
    }

    /**
     * 根据主键列表批量查询（逐条 find 并过滤 null）。
     *
     * @param ids 主键列表，为 null 或空时返回空列表
     * @return 匹配的实体列表
     */
    @Override
    public List<T> listByIds(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(this::findById)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * 按条件统计记录数。
     * <p>
     * 当前实现通过查询结果列表的 size 计数（未走 COUNT 查询），适用于中小数据量。
     *
     * @param condition 条件对象，为 null 时统计全表
     * @return 匹配的记录数
     */
    @Override
    public long count(T condition) {
        if (condition == null) {
            return findAll().size();
        }
        return queryList(condition).size();
    }

    /**
     * 判断是否存在匹配条件的记录。
     *
     * @param condition 条件对象
     * @return 存在返回 true，否则 false
     */
    @Override
    public boolean exists(T condition) {
        return count(condition) > 0;
    }
}