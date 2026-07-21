package cn.structure.infra.mongodb.repository;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.repository.GenericTypeResolver;
import cn.structure.infra.repository.RepositoryDelegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
 *   <li>ID 字段名通过 PO 类的 {@link Id} 注解自动识别，默认为 "id"</li>
 *   <li>save 委托给 {@link MongoTemplate#save(Object)}，自动判断新增或更新（依据 _id 是否存在）</li>
 *   <li>分页使用 {@link PageRequest} + count，由 MongoTemplate 生成原生分页查询</li>
 *   <li>Entity ↔ PO 转换在此层完成，Facade 层只操作领域实体</li>
 * </ul>
 *
 * @param <E>  领域实体类型
 * @param <P>  持久化对象类型（MongoDB Document）
 * @param <ID> 主键类型
 * @author chuck
 * @version 1.0.3
 * @since 2026/6/28
 */
@Slf4j
public class MongoRepositoryDelegate<E, P, ID> implements RepositoryDelegate<E, ID> {

    @Autowired
    protected MongoTemplate mongoTemplate;
    protected Class<E> entityClass;
    protected Class<P> poClass;
    protected Class<ID> idClass;
    protected String idFieldName = "id";

    public MongoRepositoryDelegate() {
        resolveGenericTypes();
        resolveIdFieldName();
        log.info("MongoRepositoryDelegate initialized: entity={}, po={}, id={}, idField={}",
                entityClass != null ? entityClass.getSimpleName() : "null",
                poClass != null ? poClass.getSimpleName() : "null",
                idClass != null ? idClass.getSimpleName() : "null",
                idFieldName);
    }

    @SuppressWarnings("unchecked")
    protected void resolveGenericTypes() {
        this.entityClass = (Class<E>) GenericTypeResolver.resolveEntityClass(getClass());
        this.poClass = (Class<P>) GenericTypeResolver.resolvePoClass(getClass());
        this.idClass = (Class<ID>) GenericTypeResolver.resolveIdClass(getClass());
    }

    protected void resolveIdFieldName() {
        if (poClass != null) {
            Field idField = findFieldWithAnnotation(poClass, Id.class);
            if (idField != null) {
                this.idFieldName = idField.getName();
            }
        }
    }

    private Field findFieldWithAnnotation(Class<?> clazz, Class<?> annotationClass) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent((Class<? extends java.lang.annotation.Annotation>) annotationClass)) {
                return field;
            }
        }
        if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
            return findFieldWithAnnotation(clazz.getSuperclass(), annotationClass);
        }
        return null;
    }

    @Override
    public Class<E> getEntityClass() {
        return entityClass;
    }

    @Override
    public Class<?> getPoClass() {
        return poClass;
    }

    @Override
    public Class<ID> getIdClass() {
        return idClass;
    }

    @Override
    public String getIdFieldName() {
        return idFieldName;
    }

    @Override
    public E save(E entity) {
        if (entity == null) {
            return null;
        }
        P po = toPo(entity);
        P savedPo = mongoTemplate.save(po);
        log.debug("Saved entity: {}", savedPo);
        return toEntity(savedPo);
    }

    @Override
    public void removeById(ID id) {
        if (id != null && poClass != null) {
            Query query = new Query(Criteria.where(idFieldName).is(id));
            mongoTemplate.remove(query, poClass);
            log.debug("Removed entity: id={}", id);
        }
    }

    @Override
    public E findById(ID id) {
        if (id == null || poClass == null) {
            return null;
        }
        Query query = new Query(Criteria.where(idFieldName).is(id));
        P po = mongoTemplate.findOne(query, poClass);
        log.debug("Find by id: id={}, found={}", id, po != null);
        return toEntity(po);
    }

    @Override
    public E queryById(ID id) {
        return findById(id);
    }

    @Override
    public Optional<E> queryByIdOptional(ID id) {
        return Optional.ofNullable(queryById(id));
    }

    @Override
    public E queryOne(E condition) {
        if (condition == null) {
            return null;
        }
        Query query = buildQuery(condition);
        P po = mongoTemplate.findOne(query, poClass);
        return toEntity(po);
    }

    @Override
    public Optional<E> queryOneOptional(E condition) {
        return Optional.ofNullable(queryOne(condition));
    }

    @Override
    public List<E> queryList(E condition) {
        if (condition == null) {
            return mongoTemplate.findAll(poClass).stream()
                    .map(this::toEntity)
                    .collect(Collectors.toList());
        }
        Query query = buildQuery(condition);
        return mongoTemplate.find(query, poClass).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public ResPage<E> queryPage(ReqPage reqPage) {
        int pageNum = reqPage.getPage() != null ? reqPage.getPage() - 1 : 0;
        int pageSize = reqPage.getSize() != null ? reqPage.getSize() : 10;

        Query query = new Query();
        long total = mongoTemplate.count(query, poClass);

        Query pageQuery = query.with(PageRequest.of(pageNum, pageSize, Sort.unsorted()));
        List<E> records = mongoTemplate.find(pageQuery, poClass).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());

        ResPage<E> resPage = new ResPage<>();
        resPage.setCurrent((long) (pageNum + 1));
        resPage.setPages(total > 0 ? (total + pageSize - 1) / pageSize : 0);
        resPage.setSize((long) pageSize);
        resPage.setTotal(total);
        resPage.setRecords(records);

        log.debug("Query page: page={}, size={}, total={}, records={}",
                pageNum + 1, pageSize, total, records.size());
        return resPage;
    }

    private Query buildQuery(E condition) {
        Query query = new Query();
        try {
            Field[] fields = getAllFields(condition.getClass());
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(condition);
                if (value != null) {
                    query.addCriteria(Criteria.where(field.getName()).is(value));
                }
            }
        } catch (Exception e) {
            log.warn("Error building query: {}", e.getMessage());
        }
        return query;
    }

    private Field[] getAllFields(Class<?> clazz) {
        List<Field> fields = new java.util.ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields.toArray(new Field[0]);
    }

    @Override
    public List<E> saveBatch(List<E> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(this::toPo)
                .map(mongoTemplate::save)
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void removeBatchByIds(List<ID> ids) {
        if (ids != null && !ids.isEmpty()) {
            Query query = new Query(Criteria.where(idFieldName).in(ids));
            mongoTemplate.remove(query, poClass);
        }
    }

    @Override
    public List<E> listByIds(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Query query = new Query(Criteria.where(idFieldName).in(ids));
        return mongoTemplate.find(query, poClass).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public long count(E condition) {
        if (condition == null) {
            return mongoTemplate.count(new Query(), poClass);
        }
        Query query = buildQuery(condition);
        return mongoTemplate.count(query, poClass);
    }

    @Override
    public boolean exists(E condition) {
        return count(condition) > 0;
    }

    protected E toEntity(P po) {
        if (po == null) {
            return null;
        }
        if (entityClass == null) {
            return (E) po;
        }
        try {
            E entity = entityClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(po, entity);
            return entity;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert PO to entity", e);
        }
    }

    protected P toPo(E entity) {
        if (entity == null) {
            return null;
        }
        if (poClass == null) {
            return (P) entity;
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