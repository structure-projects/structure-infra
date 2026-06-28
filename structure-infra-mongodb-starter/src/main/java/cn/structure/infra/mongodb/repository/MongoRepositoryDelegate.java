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
 * MongoDB 仓储委托实现
 * <p>
 * 基于 Spring Data MongoDB 实现的仓储委托
 *
 * @param <T>  持久化对象类型（PO）
 * @param <ID> 主键类型
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Slf4j
public class MongoRepositoryDelegate<T, ID> implements RepositoryDelegate<T, ID> {

    protected MongoTemplate mongoTemplate;
    protected Class<T> entityClass;
    protected String idFieldName;

    public MongoRepositoryDelegate() {
    }

    public MongoRepositoryDelegate(MongoTemplate mongoTemplate, Class<T> entityClass) {
        this(mongoTemplate, entityClass, "id");
    }

    public MongoRepositoryDelegate(MongoTemplate mongoTemplate, Class<T> entityClass, String idFieldName) {
        this.mongoTemplate = mongoTemplate;
        this.entityClass = entityClass;
        this.idFieldName = idFieldName;
        log.info("MongoRepositoryDelegate initialized for entity: {}", entityClass.getSimpleName());
    }

    public void setMongoTemplate(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void setEntityClass(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    public void setIdFieldName(String idFieldName) {
        this.idFieldName = idFieldName;
    }

    @Override
    public T save(T entity) {
        if (entity == null) {
            return null;
        }
        T saved = mongoTemplate.save(entity);
        log.debug("Saved entity: {}", saved);
        return saved;
    }

    @Override
    public void removeById(ID id) {
        if (id != null) {
            Query query = new Query(Criteria.where(idFieldName).is(id));
            mongoTemplate.remove(query, entityClass);
            log.debug("Removed entity: id={}", id);
        }
    }

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

    @Override
    public T queryById(ID id) {
        return findById(id);
    }

    @Override
    public Optional<T> queryByIdOptional(ID id) {
        return Optional.ofNullable(queryById(id));
    }

    @Override
    public T queryOne(T condition) {
        if (condition == null) {
            return null;
        }
        Query query = buildQuery(condition);
        return mongoTemplate.findOne(query, entityClass);
    }

    @Override
    public Optional<T> queryOneOptional(T condition) {
        return Optional.ofNullable(queryOne(condition));
    }

    @Override
    public List<T> queryList(T condition) {
        if (condition == null) {
            return mongoTemplate.findAll(entityClass);
        }
        Query query = buildQuery(condition);
        return mongoTemplate.find(query, entityClass);
    }

    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        int pageNum = reqPage.getPage() != null ? reqPage.getPage() - 1 : 0;
        int pageSize = reqPage.getSize() != null ? reqPage.getSize() : 10;

        Query query = new Query();
        long total = mongoTemplate.count(query, entityClass);

        Query pageQuery = query.with(PageRequest.of(pageNum, pageSize, Sort.unsorted()));
        List<T> records = mongoTemplate.find(pageQuery, entityClass);

        ResPage<T> resPage = new ResPage<>();
        resPage.setCurrent((long) (pageNum + 1));
        resPage.setPages(total > 0 ? (total + pageSize - 1) / pageSize : 0);
        resPage.setSize((long) pageSize);
        resPage.setTotal(total);
        resPage.setRecords(records);

        log.debug("Query page: page={}, size={}, total={}, records={}",
                pageNum + 1, pageSize, total, records.size());
        return resPage;
    }

    private Query buildQuery(T condition) {
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
    public List<T> saveBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(mongoTemplate::save)
                .toList();
    }

    @Override
    public void removeBatchByIds(List<ID> ids) {
        if (ids != null && !ids.isEmpty()) {
            Query query = new Query(Criteria.where(idFieldName).in(ids));
            mongoTemplate.remove(query, entityClass);
        }
    }

    @Override
    public List<T> listByIds(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Query query = new Query(Criteria.where(idFieldName).in(ids));
        return mongoTemplate.find(query, entityClass);
    }

    @Override
    public long count(T condition) {
        if (condition == null) {
            return mongoTemplate.count(new Query(), entityClass);
        }
        Query query = buildQuery(condition);
        return mongoTemplate.count(query, entityClass);
    }

    @Override
    public boolean exists(T condition) {
        return count(condition) > 0;
    }
}
