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

@Slf4j
public class JpaRepositoryDelegate<T, ID> implements RepositoryDelegate<T, ID> {

    protected EntityManager entityManager;
    protected Class<T> entityClass;

    public JpaRepositoryDelegate() {
    }

    public JpaRepositoryDelegate(EntityManager entityManager, Class<T> entityClass) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
        log.info("JpaRepositoryDelegate initialized for entity: {}", entityClass.getSimpleName());
    }

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void setEntityClass(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    @Override
    public T save(T entity) {
        if (entity == null || entityManager == null || entityClass == null) {
            return null;
        }
        T saved = entityManager.merge(entity);
        log.debug("Saved entity: {}", saved);
        return saved;
    }

    @Override
    public void removeById(ID id) {
        if (id != null) {
            T entity = findById(id);
            if (entity != null) {
                entityManager.remove(entity);
                log.debug("Removed entity: id={}", id);
            }
        }
    }

    @Override
    public T findById(ID id) {
        if (id == null) {
            return null;
        }
        T entity = entityManager.find(entityClass, id);
        log.debug("Find by id: id={}, found={}", id, entity != null);
        return entity;
    }

    @Override
    public T queryById(ID id) {
        return findById(id);
    }

    @Override
    public Optional<T> queryByIdOptional(ID id) {
        return Optional.ofNullable(findById(id));
    }

    @Override
    public T queryOne(T condition) {
        if (condition == null) {
            return null;
        }
        List<T> results = queryList(condition);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public Optional<T> queryOneOptional(T condition) {
        return Optional.ofNullable(queryOne(condition));
    }

    @Override
    public List<T> queryList(T condition) {
        if (condition == null) {
            return findAll();
        }
        return queryByCondition(condition);
    }

    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        int pageNum = reqPage.getPage() != null ? reqPage.getPage() - 1 : 0;
        int pageSize = reqPage.getSize() != null ? reqPage.getSize() : 10;

        List<T> allResults = findAll();
        int start = pageNum * pageSize;
        int end = Math.min(start + pageSize, allResults.size());
        
        List<T> pageContent = start < allResults.size() ? allResults.subList(start, end) : List.of();

        ResPage<T> resPage = new ResPage<>();
        resPage.setCurrent((long) (pageNum + 1));
        resPage.setPages((long) ((allResults.size() + pageSize - 1) / pageSize));
        resPage.setSize((long) pageSize);
        resPage.setTotal((long) allResults.size());
        resPage.setRecords(pageContent);

        log.debug("Query page: page={}, size={}, total={}, records={}",
                pageNum + 1, pageSize, allResults.size(), pageContent.size());
        return resPage;
    }

    private List<T> findAll() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        query.from(entityClass);
        return entityManager.createQuery(query).getResultList();
    }

    private List<T> queryByCondition(T condition) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        Root<T> root = query.from(entityClass);

        Predicate[] predicates = buildPredicates(cb, root, condition);
        if (predicates.length > 0) {
            query.where(predicates);
        }

        return entityManager.createQuery(query).getResultList();
    }

    private Predicate[] buildPredicates(CriteriaBuilder cb, Root<T> root, T condition) {
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
    public List<T> saveBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(entityManager::merge)
                .toList();
    }

    @Override
    public void removeBatchByIds(List<ID> ids) {
        if (ids != null) {
            ids.forEach(this::removeById);
        }
    }

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

    @Override
    public long count(T condition) {
        if (condition == null) {
            return findAll().size();
        }
        return queryList(condition).size();
    }

    @Override
    public boolean exists(T condition) {
        return count(condition) > 0;
    }
}