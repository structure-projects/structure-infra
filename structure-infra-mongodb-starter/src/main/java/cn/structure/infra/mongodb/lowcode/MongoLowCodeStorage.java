package cn.structure.infra.mongodb.lowcode;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.lowcode.model.AutoFillType;
import cn.structure.infra.lowcode.model.FieldSchema;
import cn.structure.infra.lowcode.model.FieldType;
import cn.structure.infra.lowcode.model.ResourceSchema;
import cn.structure.infra.lowcode.repository.LowCodeStorage;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MongoDB 低代码仓储实现
 * <p>
 * 基于 Spring Data MongoDB 的低代码存储实现，使用 Document 代替实体类，
 * 通过 MongoTemplate 动态操作 MongoDB 集合。
 * <p>
 * 核心特性：
 * <ul>
 *   <li>Document 动态操作：使用 Document 代替 POJO，无需定义实体类</li>
 *   <li>自动创建集合：初始化时自动创建集合和索引</li>
 *   <li>自动填充：支持创建时间、更新时间自动填充</li>
 *   <li>动态查询：根据查询条件动态构建 MongoDB 查询</li>
 *   <li>分页查询：支持分页查询，自动处理总数统计</li>
 * </ul>
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@Slf4j
public class MongoLowCodeStorage implements LowCodeStorage {

    private final ResourceSchema schema;
    private final MongoTemplate mongoTemplate;

    /**
     * 构造函数
     *
     * @param schema        资源 schema 定义
     * @param mongoTemplate MongoTemplate 实例
     */
    public MongoLowCodeStorage(ResourceSchema schema, MongoTemplate mongoTemplate) {
        this.schema = schema;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void initialize() {
        String collectionName = schema.getTableName();

        // 检查集合是否存在，不存在则创建
        boolean collectionExists = mongoTemplate.collectionExists(collectionName);
        if (!collectionExists) {
            mongoTemplate.createCollection(collectionName);
            log.info("MongoDB collection created: {}", collectionName);
        }

        // 创建索引
        createIndexes(collectionName);

        log.info("MongoDB lowcode storage initialized: {}", collectionName);
    }

    /**
     * 创建索引
     * <p>
     * 根据 schema 中的字段定义自动创建索引：
     * <ul>
     *   <li>主键字段自动创建唯一索引</li>
     *   <li>标记为 index=true 的字段创建普通索引</li>
     *   <li>标记为 unique=true 的字段创建唯一索引</li>
     * </ul>
     *
     * @param collectionName 集合名称
     */
    private void createIndexes(String collectionName) {
        for (FieldSchema field : schema.getFields().values()) {
            if (field.isPrimaryKey() || field.isIndex() || field.isUnique()) {
                Index index = new Index()
                        .on(field.getName(), field.isUnique() ? org.springframework.data.domain.Sort.Direction.ASC
                                : org.springframework.data.domain.Sort.Direction.ASC);

                if (field.isUnique()) {
                    index.unique();
                }

                mongoTemplate.indexOps(collectionName).ensureIndex(index);
                log.debug("Created index for field: {} (unique={}, index={})",
                        field.getName(), field.isUnique(), field.isIndex());
            }
        }
    }

    @Override
    public Map<String, Object> save(Map<String, Object> data) {
        Document document = new Document(data);
        fillAutoFields(document, AutoFillType.CREATE);
        fillAutoFields(document, AutoFillType.CREATE_UPDATE);

        String idField = schema.getIdFieldName();
        Object idValue = document.get(idField);

        if (idValue != null) {
            // 更新操作
            Query query = new Query(Criteria.where(idField).is(idValue));
            Document existing = mongoTemplate.findOne(query, Document.class, schema.getTableName());
            if (existing != null) {
                return doUpdate(document);
            }
        }

        return doInsert(document);
    }

    /**
     * 执行插入操作
     *
     * @param document Document 对象
     * @return 插入后的数据
     */
    private Map<String, Object> doInsert(Document document) {
        mongoTemplate.insert(document, schema.getTableName());
        return documentToMap(document);
    }

    /**
     * 执行更新操作
     *
     * @param document Document 对象
     * @return 更新后的数据
     */
    private Map<String, Object> doUpdate(Document document) {
        String idField = schema.getIdFieldName();
        Object idValue = document.get(idField);

        Query query = new Query(Criteria.where(idField).is(idValue));

        // 构建更新文档
        Update update = new Update();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            if (!idField.equals(entry.getKey())) {
                update.set(entry.getKey(), entry.getValue());
            }
        }

        mongoTemplate.updateFirst(query, update, schema.getTableName());

        return findById(idValue);
    }

    @Override
    public void removeById(Object id) {
        Query query = new Query(Criteria.where(schema.getIdFieldName()).is(id));
        mongoTemplate.remove(query, schema.getTableName());
    }

    @Override
    public Map<String, Object> findById(Object id) {
        Query query = new Query(Criteria.where(schema.getIdFieldName()).is(id));
        Document result = mongoTemplate.findOne(query, Document.class, schema.getTableName());
        return result != null ? documentToMap(result) : null;
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
        List<Document> results = mongoTemplate.find(query, Document.class, schema.getTableName());
        return documentsToMaps(results);
    }

    @Override
    public ResPage<Map<String, Object>> queryPage(ReqPage reqPage) {
        int pageNum = reqPage.getPage() != null ? reqPage.getPage().intValue() - 1 : 0;
        int pageSize = reqPage.getSize() != null ? reqPage.getSize().intValue() : 10;

        Query query = buildQuery(null);
        long total = mongoTemplate.count(query, schema.getTableName());

        ResPage<Map<String, Object>> page = new ResPage<>();
        page.setCurrent((long) pageNum + 1);
        page.setSize((long) pageSize);
        page.setTotal(total);

        if (total == 0) {
            page.setRecords(new ArrayList<>());
            page.setPages(0L);
            return page;
        }

        long pages = (total + pageSize - 1) / pageSize;
        page.setPages(pages);

        query.with(PageRequest.of(pageNum, pageSize, Sort.unsorted()));
        List<Document> records = mongoTemplate.find(query, Document.class, schema.getTableName());
        page.setRecords(documentsToMaps(records));

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
        Query query = new Query(Criteria.where(schema.getIdFieldName()).in(ids));
        mongoTemplate.remove(query, schema.getTableName());
    }

    @Override
    public List<Map<String, Object>> listByIds(List<Object> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        Query query = new Query(Criteria.where(schema.getIdFieldName()).in(ids));
        List<Document> results = mongoTemplate.find(query, Document.class, schema.getTableName());
        return documentsToMaps(results);
    }

    @Override
    public long count(Map<String, Object> queryParams) {
        Query query = buildQuery(queryParams);
        return mongoTemplate.count(query, schema.getTableName());
    }

    @Override
    public boolean exists(Map<String, Object> queryParams) {
        return count(queryParams) > 0;
    }

    /**
     * 构建 MongoDB 查询
     *
     * @param queryParams 查询参数
     * @return MongoDB Query 对象
     */
    private Query buildQuery(Map<String, Object> queryParams) {
        Query query = new Query();

        if (queryParams != null && !queryParams.isEmpty()) {
            for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
                String fieldName = entry.getKey();
                if (schema.getField(fieldName) != null && entry.getValue() != null) {
                    Criteria criteria = Criteria.where(fieldName).is(entry.getValue());
                    query.addCriteria(criteria);
                }
            }
        }

        return query;
    }

    /**
     * 填充自动字段
     *
     * @param document Document 对象
     * @param fillType 填充类型
     */
    private void fillAutoFields(Document document, AutoFillType fillType) {
        LocalDateTime now = LocalDateTime.now();
        for (FieldSchema field : schema.getFields().values()) {
            if (field.getAutoFill() == fillType) {
                String name = field.getName();
                if (!document.containsKey(name)) {
                    switch (field.getType()) {
                        case DATETIME -> document.put(name, now);
                        case DATE -> document.put(name, now.toLocalDate());
                        default -> {
                        }
                    }
                }
            }
        }
    }

    /**
     * 将 Document 转换为 Map
     *
     * @param document Document 对象
     * @return Map 对象
     */
    private Map<String, Object> documentToMap(Document document) {
        if (document == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    /**
     * 将 Document 列表转换为 Map 列表
     *
     * @param documents Document 列表
     * @return Map 列表
     */
    private List<Map<String, Object>> documentsToMaps(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Document doc : documents) {
            result.add(documentToMap(doc));
        }
        return result;
    }
}