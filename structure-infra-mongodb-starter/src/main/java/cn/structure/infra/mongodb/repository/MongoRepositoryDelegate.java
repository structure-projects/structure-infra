package cn.structure.infra.mongodb.repository;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.repository.RepositoryDelegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 基于 MongoDB 的 RepositoryDelegate 适配实现
 * <p>
 * 该类是 RepositoryDelegate SPI 在 MongoDB 存储类型下的标准实现，委托
 * {@link MongoTemplate} 完成文档的 CRUD 操作。它在仓储框架中扮演"具体存储适配层"的角色：
 * <ul>
 *   <li>上层由 {@code RepositoryFacade} 统一暴露给业务方，本类不直接面向业务</li>
 *   <li>当用户未提供自定义 Delegate 时，由 {@link MongoDelegateFactory} 自动创建本类实例</li>
 *   <li>当用户提供自定义 Delegate 子类时，由 {@link MongoDelegateBeanPostProcessor}
 *       在 Bean 初始化后自动注入 MongoTemplate 与实体类型</li>
 * </ul>
 * <p>
 * 实现说明：
 * <ul>
 *   <li>查询条件通过反射读取实体非空字段，组装为 {@link Criteria}（等值匹配）并拼装到 {@link Query}</li>
 *   <li>ID 字段名默认为 "id"，可通过构造器或 setter 自定义</li>
 *   <li>save 委托给 {@link MongoTemplate#save(Object)}，自动判断新增或更新（依据 _id 是否存在）</li>
 *   <li>分页使用 {@link PageRequest} + count，由 MongoTemplate 生成原生分页查询</li>
 * </ul>
 *
 * @param <T>  持久化对象类型（PO）
 * @param <ID> 主键类型
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Slf4j
public class MongoRepositoryDelegate<T, ID> implements RepositoryDelegate<T, ID> {

    /** MongoDB 操作模板，承担实际文档操作 */
    protected MongoTemplate mongoTemplate;
    /** PO 实体类型，用于反射读取字段与 findOne/find */
    protected Class<T> entityClass;
    /** 主键字段名，默认 "id"，可被子类覆盖 */
    protected String idFieldName;

    /**
     * 默认构造器，用于用户自定义子类场景。
     * <p>
     * 创建后由 {@link MongoDelegateBeanPostProcessor} 通过 setter 注入依赖。
     */
    public MongoRepositoryDelegate() {
    }

    /**
     * 以默认主键字段名 "id" 构造 Delegate。
     *
     * @param mongoTemplate MongoDB 操作模板
     * @param entityClass   PO 实体类型
     */
    public MongoRepositoryDelegate(MongoTemplate mongoTemplate, Class<T> entityClass) {
        this(mongoTemplate, entityClass, "id");
    }

    /**
     * 全参构造器，工厂自动创建场景使用。
     *
     * @param mongoTemplate MongoDB 操作模板
     * @param entityClass   PO 实体类型
     * @param idFieldName   主键字段名（用于构建按 ID 查询的 Criteria）
     */
    public MongoRepositoryDelegate(MongoTemplate mongoTemplate, Class<T> entityClass, String idFieldName) {
        this.mongoTemplate = mongoTemplate;
        this.entityClass = entityClass;
        this.idFieldName = idFieldName;
        log.info("MongoRepositoryDelegate initialized for entity: {}", entityClass.getSimpleName());
    }

    /**
     * 注入 MongoTemplate，供 BeanPostProcessor 在自定义子类上调用。
     *
     * @param mongoTemplate MongoDB 操作模板
     */
    public void setMongoTemplate(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
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
     * 设置主键字段名，供自定义子类覆盖默认 "id"。
     *
     * @param idFieldName 主键字段名
     */
    public void setIdFieldName(String idFieldName) {
        this.idFieldName = idFieldName;
    }

    /**
     * 保存或更新文档。
     * <p>
     * 委托给 {@link MongoTemplate#save(Object)}，由 MongoDB 依据 _id 自动判断新增或更新。
     *
     * @param entity 实体对象，为 null 时返回 null
     * @return 保存后的实体（与入参同一引用）
     */
    @Override
    public T save(T entity) {
        if (entity == null) {
            return null;
        }
        T saved = mongoTemplate.save(entity);
        log.debug("Saved entity: {}", saved);
        return saved;
    }

    /**
     * 根据主键删除文档。
     *
     * @param id 主键值，为 null 时不执行任何操作
     */
    @Override
    public void removeById(ID id) {
        if (id != null) {
            // 按主键字段构建等值条件并删除
            Query query = new Query(Criteria.where(idFieldName).is(id));
            mongoTemplate.remove(query, entityClass);
            log.debug("Removed entity: id={}", id);
        }
    }

    /**
     * 根据主键查询文档。
     *
     * @param id 主键值，为 null 时返回 null
     * @return 实体对象，未找到时返回 null
     */
    @Override
    public T findById(ID id) {
        if (id == null) {
            return null;
        }
        Query query = new Query(Criteria.where(idFieldName).is(id));
        T entity = mongoTemplate.findOne(query, entityClass);
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
        return Optional.ofNullable(queryById(id));
    }

    /**
     * 根据非空字段等值匹配查询单条记录。
     * <p>
     * 通过反射构建 {@link Query}，取首条匹配；多于一条时仅返回首条。
     *
     * @param condition 查询条件对象，为 null 时返回 null
     * @return 首条匹配记录，无匹配时返回 null
     */
    @Override
    public T queryOne(T condition) {
        if (condition == null) {
            return null;
        }
        Query query = buildQuery(condition);
        return mongoTemplate.findOne(query, entityClass);
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
     * 条件为 null 时查询全部；否则按非空字段构建等值 {@link Query}。
     *
     * @param condition 查询条件对象，可为 null
     * @return 匹配的实体列表，无匹配时返回空列表
     */
    @Override
    public List<T> queryList(T condition) {
        if (condition == null) {
            return mongoTemplate.findAll(entityClass);
        }
        Query query = buildQuery(condition);
        return mongoTemplate.find(query, entityClass);
    }

    /**
     * 分页查询。
     * <p>
     * 通过 {@link MongoTemplate#count(Query, Class)} 获取总数，
     * 再用 {@link PageRequest} 切片查询当前页记录。
     *
     * @param reqPage 分页请求（页码从 1 开始、每页大小，为 null 时取默认 1/10）
     * @return 分页结果，含当前页、总页数、总条数、当前页记录
     */
    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        // MongoTemplate 页码从 0 开始，业务页码从 1 开始，需减 1
        int pageNum = reqPage.getPage() != null ? reqPage.getPage() - 1 : 0;
        int pageSize = reqPage.getSize() != null ? reqPage.getSize() : 10;

        Query query = new Query();
        long total = mongoTemplate.count(query, entityClass);

        // 在原 Query 上叠加分页参数
        Query pageQuery = query.with(PageRequest.of(pageNum, pageSize, Sort.unsorted()));
        List<T> records = mongoTemplate.find(pageQuery, entityClass);

        ResPage<T> resPage = new ResPage<>();
        // 返回业务侧时页码再加回 1
        resPage.setCurrent((long) (pageNum + 1));
        // 总页数向上取整
        resPage.setPages(total > 0 ? (total + pageSize - 1) / pageSize : 0);
        resPage.setSize((long) pageSize);
        resPage.setTotal(total);
        resPage.setRecords(records);

        log.debug("Query page: page={}, size={}, total={}, records={}",
                pageNum + 1, pageSize, total, records.size());
        return resPage;
    }

    /**
     * 反射读取条件对象非空字段，构建等值 {@link Query}。
     *
     * @param condition 条件对象
     * @return 已填充等值 Criteria 的 Query
     */
    private Query buildQuery(T condition) {
        Query query = new Query();
        try {
            Field[] fields = getAllFields(condition.getClass());
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(condition);
                if (value != null) {
                    // 直接以字段名作为 key，等值匹配
                    query.addCriteria(Criteria.where(field.getName()).is(value));
                }
            }
        } catch (Exception e) {
            log.warn("Error building query: {}", e.getMessage());
        }
        return query;
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
     * 批量保存实体（逐条 save）。
     *
     * @param entities 实体列表，为 null 或空时返回空列表
     * @return 保存后的实体列表
     */
    @Override
    public List<T> saveBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(mongoTemplate::save)
                .toList();
    }

    /**
     * 根据主键列表批量删除（使用 $in 一次性删除）。
     *
     * @param ids 主键列表，为 null 或空时不执行任何操作
     */
    @Override
    public void removeBatchByIds(List<ID> ids) {
        if (ids != null && !ids.isEmpty()) {
            Query query = new Query(Criteria.where(idFieldName).in(ids));
            mongoTemplate.remove(query, entityClass);
        }
    }

    /**
     * 根据主键列表批量查询（使用 $in 一次性查询）。
     *
     * @param ids 主键列表，为 null 或空时返回空列表
     * @return 匹配的实体列表
     */
    @Override
    public List<T> listByIds(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Query query = new Query(Criteria.where(idFieldName).in(ids));
        return mongoTemplate.find(query, entityClass);
    }

    /**
     * 按条件统计记录数。
     *
     * @param condition 条件对象，为 null 时统计全部
     * @return 匹配的记录数
     */
    @Override
    public long count(T condition) {
        if (condition == null) {
            return mongoTemplate.count(new Query(), entityClass);
        }
        Query query = buildQuery(condition);
        return mongoTemplate.count(query, entityClass);
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
