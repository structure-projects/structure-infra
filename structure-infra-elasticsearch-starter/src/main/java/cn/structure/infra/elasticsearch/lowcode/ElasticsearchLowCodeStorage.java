package cn.structure.infra.elasticsearch.lowcode;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.lowcode.model.AutoFillType;
import cn.structure.infra.lowcode.model.FieldSchema;
import cn.structure.infra.lowcode.model.ResourceSchema;
import cn.structure.infra.lowcode.repository.LowCodeStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Elasticsearch 低代码仓储实现
 * <p>
 * 基于 Spring Data Elasticsearch 的低代码存储实现，使用 Map 代替 POJO 操作文档。
 * <p>
 * 核心特性：
 * <ul>
 *   <li>Map 动态操作：使用 Map 代替 POJO，无需定义实体类</li>
 *   <li>自动创建索引：初始化时自动创建索引</li>
 *   <li>自动填充：支持创建时间、更新时间自动填充</li>
 *   <li>动态查询：根据查询条件动态构建 Elasticsearch 查询</li>
 *   <li>分页查询：支持分页查询，自动处理总数统计</li>
 * </ul>
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@Slf4j
public class ElasticsearchLowCodeStorage implements LowCodeStorage {

    private final ResourceSchema schema;
    private final ElasticsearchOperations elasticsearchOperations;
    private final IndexCoordinates indexCoordinates;

    /**
     * 构造函数
     *
     * @param schema                   资源 schema 定义
     * @param elasticsearchOperations ElasticsearchOperations 实例
     */
    public ElasticsearchLowCodeStorage(ResourceSchema schema, ElasticsearchOperations elasticsearchOperations) {
        this.schema = schema;
        this.elasticsearchOperations = elasticsearchOperations;
        this.indexCoordinates = IndexCoordinates.of(schema.getTableName());
    }

    @Override
    public void initialize() {
        String indexName = schema.getTableName();

        // 检查索引是否存在，不存在则创建
        boolean indexExists = elasticsearchOperations.indexOps(indexCoordinates).exists();
        if (!indexExists) {
            elasticsearchOperations.indexOps(indexCoordinates).create();
            log.info("Elasticsearch index created: {}", indexName);
        }

        log.info("Elasticsearch lowcode storage initialized: {}", indexName);
    }

    @Override
    public Map<String, Object> save(Map<String, Object> data) {
        Map<String, Object> rowData = new HashMap<>(data);
        fillAutoFields(rowData, AutoFillType.CREATE);
        fillAutoFields(rowData, AutoFillType.CREATE_UPDATE);

        String idField = schema.getIdFieldName();
        Object idValue = rowData.get(idField);

        if (idValue != null) {
            // 更新操作 - 先检查是否存在
            Map<String, Object> existing = findById(idValue);
            if (existing != null) {
                return doUpdate(rowData);
            }
        }

        return doIndex(rowData);
    }

    /**
     * 执行索引操作（新增或更新）
     *
     * @param data 数据
     * @return 索引后的数据
     */
    private Map<String, Object> doIndex(Map<String, Object> data) {
        String idField = schema.getIdFieldName();
        Object idValue = data.get(idField);

        IndexQuery indexQuery = new IndexQueryBuilder()
                .withId(idValue != null ? String.valueOf(idValue) : null)
                .withObject(data)
                .build();

        String documentId = elasticsearchOperations.index(indexQuery, indexCoordinates);
        data.put(idField, documentId);

        return data;
    }

    /**
     * 执行更新操作
     *
     * @param data 数据
     * @return 更新后的数据
     */
    private Map<String, Object> doUpdate(Map<String, Object> data) {
        String idField = schema.getIdFieldName();
        Object idValue = data.get(idField);

        // 删除旧文档
        elasticsearchOperations.delete(String.valueOf(idValue), indexCoordinates);

        // 重新索引
        return doIndex(data);
    }

    @Override
    public void removeById(Object id) {
        elasticsearchOperations.delete(String.valueOf(id), indexCoordinates);
    }

    @Override
    public Map<String, Object> findById(Object id) {
        Map<String, Object> result = elasticsearchOperations.get(String.valueOf(id), Map.class, indexCoordinates);
        return result;
    }

    @Override
    public Map<String, Object> queryById(Object id) {
        return findById(id);
    }

    @Override
    public Map<String, Object> queryOne(Map<String, Object> queryParams) {
        List<Map<String, Object>> list = queryList(queryParams);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Optional<Map<String, Object>> queryOneOptional(Map<String, Object> queryParams) {
        return Optional.ofNullable(queryOne(queryParams));
    }

    @Override
    public List<Map<String, Object>> queryList(Map<String, Object> queryParams) {
        Query query = buildQuery(queryParams);
        SearchHits<Map<String, Object>> searchHits = elasticsearchOperations.search(query,
                (Class<Map<String, Object>>) (Class<?>) Map.class, indexCoordinates);

        List<Map<String, Object>> results = new ArrayList<>();
        for (SearchHit<Map<String, Object>> hit : searchHits.getSearchHits()) {
            results.add(hit.getContent());
        }
        return results;
    }

    @Override
    public ResPage<Map<String, Object>> queryPage(ReqPage reqPage) {
        int pageNum = reqPage.getPage() != null ? reqPage.getPage().intValue() - 1 : 0;
        int pageSize = reqPage.getSize() != null ? reqPage.getSize().intValue() : 10;

        Query query = buildQuery(null);
        PageRequest pageRequest = PageRequest.of(pageNum, pageSize, Sort.unsorted());
        query.setPageable(pageRequest);

        SearchHits<Map<String, Object>> searchHits = elasticsearchOperations.search(query,
                (Class<Map<String, Object>>) (Class<?>) Map.class, indexCoordinates);

        ResPage<Map<String, Object>> page = new ResPage<>();
        page.setCurrent((long) pageNum + 1);
        page.setSize((long) pageSize);
        page.setTotal(searchHits.getTotalHits());

        if (searchHits.getTotalHits() == 0) {
            page.setRecords(new ArrayList<>());
            page.setPages(0L);
            return page;
        }

        long pages = (searchHits.getTotalHits() + pageSize - 1) / pageSize;
        page.setPages(pages);

        List<Map<String, Object>> records = new ArrayList<>();
        for (SearchHit<Map<String, Object>> hit : searchHits.getSearchHits()) {
            records.add(hit.getContent());
        }
        page.setRecords(records);

        return page;
    }

    @Override
    public List<Map<String, Object>> saveBatch(List<Map<String, Object>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> data : dataList) {
            result.add(save(data));
        }
        return result;
    }

    @Override
    public void removeBatchByIds(List<Object> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Object id : ids) {
            elasticsearchOperations.delete(String.valueOf(id), indexCoordinates);
        }
    }

    @Override
    public List<Map<String, Object>> listByIds(List<Object> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Object id : ids) {
            Map<String, Object> doc = findById(id);
            if (doc != null) {
                results.add(doc);
            }
        }
        return results;
    }

    @Override
    public long count(Map<String, Object> queryParams) {
        Query query = buildQuery(queryParams);
        return elasticsearchOperations.count(query, indexCoordinates);
    }

    @Override
    public boolean exists(Map<String, Object> queryParams) {
        return count(queryParams) > 0;
    }

    /**
     * 构建 Elasticsearch 查询
     *
     * @param queryParams 查询参数
     * @return Elasticsearch Query 对象
     */
    private Query buildQuery(Map<String, Object> queryParams) {
        Criteria criteria = new Criteria();

        if (queryParams != null && !queryParams.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
                String fieldName = entry.getKey();
                if (schema.getField(fieldName) != null && entry.getValue() != null) {
                    if (first) {
                        criteria = Criteria.where(fieldName).is(entry.getValue());
                        first = false;
                    } else {
                        criteria = criteria.and(Criteria.where(fieldName).is(entry.getValue()));
                    }
                }
            }
        } else {
            // 查询所有
            criteria = Criteria.where("_id").exists();
        }

        return new CriteriaQuery(criteria);
    }

    /**
     * 填充自动字段
     *
     * @param data     数据
     * @param fillType 填充类型
     */
    private void fillAutoFields(Map<String, Object> data, AutoFillType fillType) {
        LocalDateTime now = LocalDateTime.now();
        for (FieldSchema field : schema.getFields().values()) {
            if (field.getAutoFill() == fillType) {
                String name = field.getName();
                if (!data.containsKey(name)) {
                    switch (field.getType()) {
                        case DATETIME -> data.put(name, now);
                        case DATE -> data.put(name, now.toLocalDate());
                        default -> {
                        }
                    }
                }
            }
        }
    }
}