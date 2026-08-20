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

package cn.structure.infra.elasticsearch.repository;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.repository.GenericTypeResolver;
import cn.structure.infra.repository.RepositoryDelegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;

import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 基于 Elasticsearch 的 RepositoryDelegate 适配实现
 * <p>
 * 该类是 RepositoryDelegate SPI 在 Elasticsearch 存储类型下的标准实现，委托
 * {@link ElasticsearchOperations} 完成文档的 CRUD 操作。它在仓储框架中扮演"具体存储适配层"的角色：
 * <ul>
 *   <li>上层由 {@code RepositoryFacade} 统一暴露给业务方，本类不直接面向业务</li>
 *   <li>当用户未提供自定义 Delegate 时，由 {@link ElasticsearchDelegateFactory} 自动创建本类实例</li>
 *   <li>当用户提供自定义 Delegate 子类时，由 {@link ElasticsearchDelegateBeanPostProcessor}
 *       在 Bean 初始化后自动注入 ElasticsearchOperations 与实体类型</li>
 * </ul>
 * <p>
 * 实现说明：
 * <ul>
 *   <li>ID 统一转换为 String：ES 文档 ID 必须为字符串，所有按 ID 操作均通过 {@code String.valueOf(id)} 转换</li>
 *   <li>ID 字段名通过 PO 类的 {@link Id} 注解自动识别，默认为 "id"</li>
 *   <li>查询条件通过反射读取实体非空字段，组装为 {@link Criteria}（等值匹配）并构建 {@link CriteriaQuery}</li>
 *   <li>save 委托给 {@link ElasticsearchOperations#save(Object)}，由 ES 自动判断新增或覆盖索引</li>
 *   <li>分页使用 {@link PageRequest}，由 ES 原生分页（from/size）实现</li>
 *   <li>常作为 CQRS 读侧：适用于全文检索、聚合分析等读多写少场景</li>
 *   <li>Entity ↔ PO 转换在此层完成，Facade 层只操作领域实体</li>
 * </ul>
 *
 * @param <E> 领域实体类型
 * @param <P> 持久化对象类型（ES Document）
 * @param <ID> 主键类型
 * @author chuck
 * @version 1.0.3
 * @since 2026/6/28
 */
@Slf4j
public class ElasticsearchRepositoryDelegate<E, P, ID> implements RepositoryDelegate<E, ID> {

    @Autowired
    protected ElasticsearchOperations elasticsearchOperations;
    private volatile Class<E> entityClass;
    private volatile Class<P> poClass;
    private volatile Class<ID> idClass;
    private volatile String idFieldName;

    public ElasticsearchRepositoryDelegate() {
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
        P savedPo = elasticsearchOperations.save(po);
        log.debug("Saved entity: {}", savedPo);
        return toEntity(savedPo);
    }

    @Override
    public void removeById(ID id) {
        if (id != null) {
            elasticsearchOperations.delete(String.valueOf(id), getPoClass());
            log.debug("Removed entity: id={}", id);
        }
    }

    @Override
    public E findById(ID id) {
        if (id == null) {
            return null;
        }
        P po = elasticsearchOperations.get(String.valueOf(id), getPoClass());
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
        SearchHits<P> searchHits = elasticsearchOperations.search(query, getPoClass());
        P po = searchHits.hasSearchHits() ? searchHits.getSearchHit(0).getContent() : null;
        return toEntity(po);
    }

    @Override
    public Optional<E> queryOneOptional(E condition) {
        return Optional.ofNullable(queryOne(condition));
    }

    @Override
    public List<E> queryList(E condition) {
        if (condition == null) {
            Query query = new CriteriaQuery(Criteria.where("*").exists());
            SearchHits<P> searchHits = elasticsearchOperations.search(query, getPoClass());
            return searchHits.getSearchHits().stream()
                    .map(hit -> toEntity(hit.getContent()))
                    .collect(Collectors.toList());
        }
        P poCondition = toPo(condition);
        Query query = buildQuery(poCondition);
        SearchHits<P> searchHits = elasticsearchOperations.search(query, getPoClass());
        return searchHits.getSearchHits().stream()
                .map(hit -> toEntity(hit.getContent()))
                .collect(Collectors.toList());
    }

    @Override
    public ResPage<E> queryPage(ReqPage reqPage) {
        int pageNum = reqPage.getPage() != null ? reqPage.getPage() - 1 : 0;
        int pageSize = reqPage.getSize() != null ? reqPage.getSize() : 10;

        Query query = new CriteriaQuery(Criteria.where("*").exists());
        PageRequest pageRequest = PageRequest.of(pageNum, pageSize, Sort.unsorted());
        query.setPageable(pageRequest);

        SearchHits<P> searchHits = elasticsearchOperations.search(query, getPoClass());

        ResPage<E> resPage = new ResPage<>();
        resPage.setCurrent((long) (pageNum + 1));
        resPage.setPages((long) (searchHits.getTotalHits() > 0 ? (searchHits.getTotalHits() + pageSize - 1) / pageSize : 0));
        resPage.setSize((long) pageSize);
        resPage.setTotal(searchHits.getTotalHits());
        resPage.setRecords(searchHits.getSearchHits().stream()
                .map(hit -> toEntity(hit.getContent()))
                .collect(Collectors.toList()));

        log.debug("Query page: page={}, size={}, total={}, records={}",
                pageNum + 1, pageSize, searchHits.getTotalHits(), resPage.getRecords().size());
        return resPage;
    }

    private Query buildQuery(P condition) {
        Criteria criteria = new Criteria();
        try {
            Field[] fields = getAllFields(condition.getClass());
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(condition);
                if (value != null) {
                    criteria = criteria.and(Criteria.where(field.getName()).is(value));
                }
            }
        } catch (Exception e) {
            log.warn("Error building query: {}", e.getMessage());
        }
        return new CriteriaQuery(criteria);
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
                .map(elasticsearchOperations::save)
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void removeBatchByIds(List<ID> ids) {
        if (ids != null && !ids.isEmpty()) {
            ids.forEach(id -> elasticsearchOperations.delete(String.valueOf(id), getPoClass()));
        }
    }

    @Override
    public List<E> listByIds(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(this::findById)
                .filter(entity -> entity != null)
                .collect(Collectors.toList());
    }

    @Override
    public long count(E condition) {
        if (condition == null) {
            Query query = new CriteriaQuery(Criteria.where("*").exists());
            return elasticsearchOperations.count(query, getPoClass());
        }
        P poCondition = toPo(condition);
        Query query = buildQuery(poCondition);
        return elasticsearchOperations.count(query, getPoClass());
    }

    @Override
    public boolean exists(E condition) {
        return count(condition) > 0;
    }

    protected E toEntity(P po) {
        if (po == null) {
            return null;
        }
        Class<E> entityType = getEntityClass();
        if (entityType == null) {
            return (E) po;
        }
        try {
            E entity = entityType.getDeclaredConstructor().newInstance();
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
        Class<P> poType = getPoClass();
        if (poType == null) {
            return (P) entity;
        }
        try {
            P po = poType.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(entity, po);
            return po;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert entity to PO", e);
        }
    }
}