package cn.structure.infra.jpa.repository;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.repository.GenericTypeResolver;
import cn.structure.infra.repository.RepositoryDelegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
 *   <li>分页使用 CriteriaBuilder 创建 COUNT 查询和物理分页，避免内存分页性能问题</li>
 *   <li>ID 字段名通过 PO 类的 {@link Id} 注解自动识别，默认为 "id"</li>
 *   <li>Entity ↔ PO 转换在此层完成，Facade 层只操作领域实体</li>
 * </ul>
 *
 * @param <E>  领域实体类型
 * @param <P>  持久化对象类型（JPA Entity）
 * @param <ID> 主键类型
 * @author chuck
 * @version 1.0.3
 * @since 2026/6/28
 */
@Slf4j
public class JpaRepositoryDelegate<E, P, ID> implements RepositoryDelegate<E, ID> {

    @Autowired
    protected EntityManager entityManager;
    protected Class<E> entityClass;
    protected Class<P> poClass;
    protected Class<ID> idClass;
    protected String idFieldName = "id";

    public JpaRepositoryDelegate() {
        resolveGenericTypes();
        resolveIdFieldName();
        log.info("JpaRepositoryDelegate initialized: entity={}, po={}, id={}, idField={}",
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
        if (entity == null || entityManager == null || poClass == null) {
            return null;
        }
        P po = toPo(entity);
        P savedPo = entityManager.merge(po);
        log.debug("Saved entity: {}", savedPo);
        return toEntity(savedPo);
    }

    @Override
    public void removeById(ID id) {
        if (id != null && entityManager != null && poClass != null) {
            P po = entityManager.find(poClass, id);
            if (po != null) {
                entityManager.remove(po);
                log.debug("Removed entity: id={}", id);
            }
        }
    }

    @Override
    public E findById(ID id) {
        if (id == null || entityManager == null || poClass == null) {
            return null;
        }
        P po = entityManager.find(poClass, id);
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
        List<E> results = queryList(condition);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public Optional<E> queryOneOptional(E condition) {
        return Optional.ofNullable(queryOne(condition));
    }

    @Override
    public List<E> queryList(E condition) {
        if (condition == null) {
            return findAll();
        }
        return queryByCondition(condition);
    }

    @Override
    public ResPage<E> queryPage(ReqPage reqPage) {
        if (entityManager == null || poClass == null) {
            ResPage<E> emptyPage = new ResPage<>();
            emptyPage.setCurrent(1L);
            emptyPage.setPages(0L);
            emptyPage.setSize(10L);
            emptyPage.setTotal(0L);
            emptyPage.setRecords(List.of());
            return emptyPage;
        }

        int pageNum = reqPage.getPage() != null ? reqPage.getPage() - 1 : 0;
        int pageSize = reqPage.getSize() != null ? reqPage.getSize() : 10;

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        long total = executeCountQuery(cb);

        List<E> pageContent;
        if (total > 0) {
            CriteriaQuery<P> query = cb.createQuery(poClass);
            query.from(poClass);

            TypedQuery<P> typedQuery = entityManager.createQuery(query);
            typedQuery.setFirstResult(pageNum * pageSize);
            typedQuery.setMaxResults(pageSize);

            pageContent = typedQuery.getResultList().stream()
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

    private long executeCountQuery(CriteriaBuilder cb) {
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        countQuery.select(cb.count(countQuery.from(poClass)));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private List<E> findAll() {
        if (entityManager == null || poClass == null) {
            return List.of();
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<P> query = cb.createQuery(poClass);
        query.from(poClass);
        return entityManager.createQuery(query).getResultList().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    private List<E> queryByCondition(E condition) {
        if (entityManager == null || poClass == null) {
            return List.of();
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<P> query = cb.createQuery(poClass);
        Root<P> root = query.from(poClass);

        Predicate[] predicates = buildPredicates(cb, root, condition);
        if (predicates.length > 0) {
            query.where(predicates);
        }

        return entityManager.createQuery(query).getResultList().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    private Predicate[] buildPredicates(CriteriaBuilder cb, Root<P> root, E condition) {
        List<Predicate> predicates = new java.util.ArrayList<>();
        try {
            Field[] fields = getAllFields(condition.getClass());
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(condition);
                if (value != null) {
                    predicates.add(cb.equal(root.get(field.getName()), value));
                }
            }
        } catch (Exception e) {
            log.warn("Error building predicates: {}", e.getMessage());
        }
        return predicates.toArray(new Predicate[0]);
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
                .map(entityManager::merge)
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void removeBatchByIds(List<ID> ids) {
        if (ids != null) {
            ids.forEach(this::removeById);
        }
    }

    @Override
    public List<E> listByIds(List<ID> ids) {
        if (ids == null || ids.isEmpty() || entityManager == null || poClass == null) {
            return List.of();
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<P> query = cb.createQuery(poClass);
        Root<P> root = query.from(poClass);

        query.where(root.get(idFieldName).in(ids));

        return entityManager.createQuery(query).getResultList().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public long count(E condition) {
        if (entityManager == null || poClass == null) {
            return 0;
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        if (condition == null) {
            return executeCountQuery(cb);
        }

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<P> root = countQuery.from(poClass);
        countQuery.select(cb.count(root));

        Predicate[] predicates = buildPredicates(cb, root, condition);
        if (predicates.length > 0) {
            countQuery.where(predicates);
        }

        return entityManager.createQuery(countQuery).getSingleResult();
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