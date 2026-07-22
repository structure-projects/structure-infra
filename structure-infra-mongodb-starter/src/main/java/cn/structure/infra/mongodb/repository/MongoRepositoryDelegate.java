package cn.structure.infra.mongodb.repository;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.repository.GenericTypeResolver;
import cn.structure.infra.repository.RepositoryDelegate;
import jakarta.persistence.Id;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class MongoRepositoryDelegate<E, P, ID> implements RepositoryDelegate<E, ID> {

    @Autowired
    protected MongoTemplate mongoTemplate;

    private volatile Class<E> entityClass;
    private volatile Class<P> poClass;
    private volatile Class<ID> idClass;
    private volatile String idFieldName;

    public MongoRepositoryDelegate() {
    }

    @Override
    public Class<E> getEntityClass() {
        if (entityClass == null) {
            synchronized (this) {
                if (entityClass == null) {
                    entityClass = resolveEntityClass();
                    log.debug("Resolved entityClass: {}", entityClass != null ? entityClass.getSimpleName() : "null");
                }
            }
        }
        return entityClass;
    }

    @Override
    public Class<P> getPoClass() {
        if (poClass == null) {
            synchronized (this) {
                if (poClass == null) {
                    poClass = resolvePoClass();
                    log.debug("Resolved poClass: {}", poClass != null ? poClass.getSimpleName() : "null");
                }
            }
        }
        return poClass;
    }

    @SuppressWarnings("unchecked")
    private Class<P> getPoClassInternal() {
        return (Class<P>) getPoClass();
    }

    @Override
    public Class<ID> getIdClass() {
        if (idClass == null) {
            synchronized (this) {
                if (idClass == null) {
                    idClass = resolveIdClass();
                    log.debug("Resolved idClass: {}", idClass != null ? idClass.getSimpleName() : "null");
                }
            }
        }
        return idClass;
    }

    @Override
    public String getIdFieldName() {
        if (idFieldName == null) {
            synchronized (this) {
                if (idFieldName == null) {
                    idFieldName = resolveIdFieldName();
                    log.debug("Resolved idFieldName: {}", idFieldName);
                }
            }
        }
        return idFieldName;
    }

    @SuppressWarnings("unchecked")
    private Class<E> resolveEntityClass() {
        try {
            return (Class<E>) GenericTypeResolver.resolveEntityClass(getClass());
        } catch (Exception e) {
            log.warn("Cannot resolve entityClass: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Class<P> resolvePoClass() {
        try {
            return (Class<P>) GenericTypeResolver.resolvePoClass(getClass());
        } catch (Exception e) {
            log.warn("Cannot resolve poClass: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Class<ID> resolveIdClass() {
        try {
            return (Class<ID>) GenericTypeResolver.resolveIdClass(getClass());
        } catch (Exception e) {
            log.warn("Cannot resolve idClass: {}", e.getMessage());
            return null;
        }
    }

    private String resolveIdFieldName() {
        Class<?> poType = getPoClass();
        if (poType != null) {
            Field idField = findFieldWithAnnotation(poType, Id.class);
            if (idField != null) {
                return idField.getName();
            }
        }
        return "id";
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
        if (id != null) {
            Query query = new Query(Criteria.where(getIdFieldName()).is(id));
            mongoTemplate.remove(query, getPoClassInternal());
            log.debug("Removed entity: id={}", id);
        }
    }

    @Override
    public E findById(ID id) {
        if (id == null) {
            return null;
        }
        Query query = new Query(Criteria.where(getIdFieldName()).is(id));
        P po = mongoTemplate.findOne(query, getPoClassInternal());
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
        P poCondition = toPo(condition);
        Query query = buildQuery(poCondition);
        P po = mongoTemplate.findOne(query, getPoClassInternal());
        return toEntity(po);
    }

    @Override
    public Optional<E> queryOneOptional(E condition) {
        return Optional.ofNullable(queryOne(condition));
    }

    @Override
    public List<E> queryList(E condition) {
        if (condition == null) {
            List<P> poList = mongoTemplate.findAll(getPoClassInternal());
            return toEntityList(poList);
        }
        P poCondition = toPo(condition);
        Query query = buildQuery(poCondition);
        List<P> poList = mongoTemplate.find(query, getPoClassInternal());
        return toEntityList(poList);
    }

    @Override
    public ResPage<E> queryPage(ReqPage reqPage) {
        int pageNum = reqPage.getPage() != null ? reqPage.getPage() - 1 : 0;
        int pageSize = reqPage.getSize() != null ? reqPage.getSize() : 10;

        Query query = new Query();
        long total = mongoTemplate.count(query, getPoClassInternal());

        List<E> pageContent;
        if (total > 0) {
            query.with(PageRequest.of(pageNum, pageSize, Sort.unsorted()));
            pageContent = mongoTemplate.find(query, getPoClassInternal()).stream()
                    .map(this::toEntity)
                    .collect(Collectors.toList());
        } else {
            pageContent = List.of();
        }

        ResPage<E> resPage = new ResPage<>();
        resPage.setCurrent((long) (pageNum + 1));
        resPage.setPages(total > 0 ? (total + pageSize - 1) / pageSize : 0);
        resPage.setSize((long) pageSize);
        resPage.setTotal(total);
        resPage.setRecords(pageContent);

        log.debug("Query page: page={}, size={}, total={}, records={}",
                pageNum + 1, pageSize, total, pageContent.size());
        return resPage;
    }

    private Query buildQuery(P condition) {
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
        List<P> poList = entities.stream()
                .map(this::toPo)
                .collect(Collectors.toList());
        mongoTemplate.insert(poList, getPoClassInternal());
        return toEntityList(poList);
    }

    @Override
    public void removeBatchByIds(List<ID> ids) {
        if (ids != null && !ids.isEmpty()) {
            Query query = new Query(Criteria.where(getIdFieldName()).in(ids));
            mongoTemplate.remove(query, getPoClassInternal());
        }
    }

    @Override
    public List<E> listByIds(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Query query = new Query(Criteria.where(getIdFieldName()).in(ids));
        List<P> poList = mongoTemplate.find(query, getPoClassInternal());
        return toEntityList(poList);
    }

    @Override
    public long count(E condition) {
        if (condition == null) {
            return mongoTemplate.count(new Query(), getPoClassInternal());
        }
        P poCondition = toPo(condition);
        Query query = buildQuery(poCondition);
        return mongoTemplate.count(query, getPoClassInternal());
    }

    @Override
    public boolean exists(E condition) {
        return count(condition) > 0;
    }

    protected E toEntity(P po) {
        if (po == null) {
            return null;
        }
        try {
            E entity = getEntityClass().getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(po, entity);
            return entity;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert PO to entity", e);
        }
    }

    protected List<E> toEntityList(List<P> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    protected P toPo(E entity) {
        if (entity == null) {
            return null;
        }
        try {
            P po = ((Class<P>) getPoClass()).getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(entity, po);
            return po;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert entity to PO", e);
        }
    }
}