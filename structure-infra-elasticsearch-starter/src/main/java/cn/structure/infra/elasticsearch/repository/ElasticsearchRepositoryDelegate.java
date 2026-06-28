package cn.structure.infra.elasticsearch.repository;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.repository.RepositoryDelegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Elasticsearch 仓储委托实现
 * <p>
 * 基于 Spring Data Elasticsearch 实现的仓储委托
 *
 * @param <T>  持久化对象类型（PO）
 * @param <ID> 主键类型
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Slf4j
public class ElasticsearchRepositoryDelegate<T, ID> implements RepositoryDelegate<T, ID> {

    protected ElasticsearchOperations elasticsearchOperations;
    protected Class<T> entityClass;
    protected String idFieldName;

    public ElasticsearchRepositoryDelegate() {
    }

    public ElasticsearchRepositoryDelegate(ElasticsearchOperations elasticsearchOperations, Class<T> entityClass) {
        this(elasticsearchOperations, entityClass, "id");
    }

    public ElasticsearchRepositoryDelegate(ElasticsearchOperations elasticsearchOperations, Class<T> entityClass, String idFieldName) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.entityClass = entityClass;
        this.idFieldName = idFieldName;
        log.info("ElasticsearchRepositoryDelegate initialized for entity: {}", entityClass.getSimpleName());
    }

    @Override
    public T save(T entity) {
        if (entity == null) {
            return null;
        }
        T saved = elasticsearchOperations.save(entity);
        log.debug("Saved entity: {}", saved);
        return saved;
    }

    @Override
    public void removeById(ID id) {
        if (id != null) {
            elasticsearchOperations.delete(String.valueOf(id), entityClass);
            log.debug("Removed entity: id={}", id);
        }
    }

    @Override
    public T findById(ID id) {
        if (id == null) {
            return null;
        }
        T entity = elasticsearchOperations.get(String.valueOf(id), entityClass);
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
        SearchHits<T> searchHits = elasticsearchOperations.search(query, entityClass);
        return searchHits.hasSearchHits() ? searchHits.getSearchHit(0).getContent() : null;
    }

    @Override
    public Optional<T> queryOneOptional(T condition) {
        return Optional.ofNullable(queryOne(condition));
    }

    @Override
    public List<T> queryList(T condition) {
        if (condition == null) {
            Query query = new CriteriaQuery(Criteria.where("*").exists());
            SearchHits<T> searchHits = elasticsearchOperations.search(query, entityClass);
            return searchHits.getSearchHits().stream()
                    .map(hit -> hit.getContent())
                    .collect(Collectors.toList());
        }
        Query query = buildQuery(condition);
        SearchHits<T> searchHits = elasticsearchOperations.search(query, entityClass);
        return searchHits.getSearchHits().stream()
                .map(hit -> hit.getContent())
                .collect(Collectors.toList());
    }

    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        int pageNum = reqPage.getPage() != null ? reqPage.getPage() - 1 : 0;
        int pageSize = reqPage.getSize() != null ? reqPage.getSize() : 10;

        Query query = new CriteriaQuery(Criteria.where("*").exists());
        PageRequest pageRequest = PageRequest.of(pageNum, pageSize, Sort.unsorted());
        query.setPageable(pageRequest);

        SearchHits<T> searchHits = elasticsearchOperations.search(query, entityClass);

        ResPage<T> resPage = new ResPage<>();
        resPage.setCurrent((long) (pageNum + 1));
        resPage.setPages((long) (searchHits.getTotalHits() > 0 ? (searchHits.getTotalHits() + pageSize - 1) / pageSize : 0));
        resPage.setSize((long) pageSize);
        resPage.setTotal(searchHits.getTotalHits());
        resPage.setRecords(searchHits.getSearchHits().stream()
                .map(hit -> hit.getContent())
                .collect(Collectors.toList()));

        log.debug("Query page: page={}, size={}, total={}, records={}",
                pageNum + 1, pageSize, searchHits.getTotalHits(), resPage.getRecords().size());
        return resPage;
    }

    private Query buildQuery(T condition) {
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
    public List<T> saveBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(elasticsearchOperations::save)
                .collect(Collectors.toList());
    }

    @Override
    public void removeBatchByIds(List<ID> ids) {
        if (ids != null && !ids.isEmpty()) {
            ids.forEach(id -> elasticsearchOperations.delete(String.valueOf(id), entityClass));
        }
    }

    @Override
    public List<T> listByIds(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(this::findById)
                .filter(entity -> entity != null)
                .collect(Collectors.toList());
    }

    @Override
    public long count(T condition) {
        if (condition == null) {
            Query query = new CriteriaQuery(Criteria.where("*").exists());
            return elasticsearchOperations.count(query, entityClass);
        }
        Query query = buildQuery(condition);
        return elasticsearchOperations.count(query, entityClass);
    }

    @Override
    public boolean exists(T condition) {
        return count(condition) > 0;
    }
}
