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

    /**
     * 初始化存储结构：自动创建 MongoDB 集合与索引。
     * <p>
     * 若集合不存在则调用 {@link MongoTemplate#createCollection(String)} 创建；
     * 随后根据 schema 中的字段定义创建主键、唯一、普通索引。
     */
    @Override
    public void initialize() {
        String collectionName = schema.getTableName();

        // 检查集合是否存在，不存在则创建（MongoDB 集合无需预定义 schema）
        boolean collectionExists = mongoTemplate.collectionExists(collectionName);
        if (!collectionExists) {
            mongoTemplate.createCollection(collectionName);
            log.info("MongoDB collection created: {}", collectionName);
        }

        // 根据 schema 字段定义自动创建索引
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
            // 主键、索引、唯一字段均需创建索引
            if (field.isPrimaryKey() || field.isIndex() || field.isUnique()) {
                Index index = new Index()
                        .on(field.getName(), field.isUnique() ? org.springframework.data.domain.Sort.Direction.ASC
                                : org.springframework.data.domain.Sort.Direction.ASC);

                // 唯一字段追加 unique 约束
                if (field.isUnique()) {
                    index.unique();
                }

                mongoTemplate.indexOps(collectionName).ensureIndex(index);
                log.debug("Created index for field: {} (unique={}, index={})",
                        field.getName(), field.isUnique(), field.isIndex());
            }
        }
    }

    /**
     * 保存或更新一条记录（以 Map 形式）。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>将 Map 转为 MongoDB {@link Document}</li>
     *   <li>按 {@link AutoFillType#CREATE} 与 {@link AutoFillType#CREATE_UPDATE} 自动填充时间字段</li>
     *   <li>根据主键是否存在且库中已有同 ID 文档，决定走 doUpdate 或 doInsert</li>
     * </ol>
     *
     * @param data 数据 Map，键为字段名、值为字段值
     * @return 保存后的数据（含自动填充字段）
     */
    @Override
    public Map<String, Object> save(Map<String, Object> data) {
        // Map → Document 转换，MongoDB 原生操作基于 Document
        Document document = new Document(data);
        // 创建场景填充：CREATE_TIME 等
        fillAutoFields(document, AutoFillType.CREATE);
        // 创建/更新双重填充：CREATE_UPDATE 字段
        fillAutoFields(document, AutoFillType.CREATE_UPDATE);

        String idField = schema.getIdFieldName();
        Object idValue = document.get(idField);

        if (idValue != null) {
            // 已带主键时先查库，存在则更新、不存在则插入
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
     * @return 插入后的数据（Document → Map 转换后返回）
     */
    private Map<String, Object> doInsert(Document document) {
        mongoTemplate.insert(document, schema.getTableName());
        // Document → Map 转换，对外统一暴露 Map 接口
        return documentToMap(document);
    }

    /**
     * 执行更新操作
     *
     * @param document Document 对象
     * @return 更新后的数据（重新查询并以 Map 形式返回）
     */
    private Map<String, Object> doUpdate(Document document) {
        String idField = schema.getIdFieldName();
        Object idValue = document.get(idField);

        Query query = new Query(Criteria.where(idField).is(idValue));

        // 构建 $set 更新文档：跳过主键字段
        Update update = new Update();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            if (!idField.equals(entry.getKey())) {
                update.set(entry.getKey(), entry.getValue());
            }
        }

        mongoTemplate.updateFirst(query, update, schema.getTableName());

        // 更新后重新查询以返回最新状态（Document → Map）
        return findById(idValue);
    }

    /**
     * 根据主键删除文档。
     *
     * @param id 主键值
     */
    @Override
    public void removeById(Object id) {
        Query query = new Query(Criteria.where(schema.getIdFieldName()).is(id));
        mongoTemplate.remove(query, schema.getTableName());
    }

    /**
     * 根据主键查询单条记录。
     * <p>
     * 查询结果 Document 通过 {@link #documentToMap(Document)} 转为 Map 返回。
     *
     * @param id 主键值
     * @return 数据 Map，未找到时返回 null
     */
    @Override
    public Map<String, Object> findById(Object id) {
        Query query = new Query(Criteria.where(schema.getIdFieldName()).is(id));
        Document result = mongoTemplate.findOne(query, Document.class, schema.getTableName());
        return result != null ? documentToMap(result) : null;
    }

    /**
     * 根据主键查询（与 findById 等价，语义上用于"读模型"）。
     *
     * @param id 主键值
     * @return 数据 Map，未找到时返回 null
     */
    @Override
    public Map<String, Object> queryById(Object id) {
        return findById(id);
    }

    /**
     * 根据条件查询单条记录，取结果集首条。
     *
     * @param queryParams 查询条件 Map，键为字段名、值为等值匹配值
     * @return 首条匹配记录，无匹配时返回 null
     */
    @Override
    public Map<String, Object> queryOne(Map<String, Object> queryParams) {
        List<Map<String, Object>> list = queryList(queryParams);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 根据条件查询单条记录，并以 {@link Optional} 包装返回。
     *
     * @param queryParams 查询条件 Map
     * @return 包含首条匹配记录的 Optional
     */
    @Override
    public Optional<Map<String, Object>> queryOneOptional(Map<String, Object> queryParams) {
        return Optional.ofNullable(queryOne(queryParams));
    }

    /**
     * 根据条件等值匹配查询列表。
     * <p>
     * 仅 schema 中存在且非 null 的字段才会进入查询 Criteria。查询结果 Document 列表
     * 通过 {@link #documentsToMaps(List)} 转为 Map 列表返回。
     *
     * @param queryParams 查询条件 Map，为 null 或空时等价于全集合查询
     * @return 匹配记录列表，无匹配时返回空列表
     */
    @Override
    public List<Map<String, Object>> queryList(Map<String, Object> queryParams) {
        Query query = buildQuery(queryParams);
        List<Document> results = mongoTemplate.find(query, Document.class, schema.getTableName());
        // Document 列表 → Map 列表转换
        return documentsToMaps(results);
    }

    /**
     * 分页查询。
     * <p>
     * 通过 {@link MongoTemplate#count(Query, String)} 获取总数，再用 {@link PageRequest} 切片
     * 查询当前页 Document，并转为 Map 列表。total 为 0 时直接返回空记录。
     *
     * @param reqPage 分页请求（页码从 1 开始、每页大小，为 null 时取默认 1/10）
     * @return 分页结果，含当前页、总页数、总条数、当前页记录
     */
    @Override
    public ResPage<Map<String, Object>> queryPage(ReqPage reqPage) {
        // MongoTemplate 页码从 0 开始，业务页码从 1 开始，需减 1
        int pageNum = reqPage.getPage() != null ? reqPage.getPage().intValue() - 1 : 0;
        int pageSize = reqPage.getSize() != null ? reqPage.getSize().intValue() : 10;

        Query query = buildQuery(null);
        long total = mongoTemplate.count(query, schema.getTableName());

        ResPage<Map<String, Object>> page = new ResPage<>();
        // 返回业务侧时页码再加回 1
        page.setCurrent((long) pageNum + 1);
        page.setSize((long) pageSize);
        page.setTotal(total);

        if (total == 0) {
            // 无数据时直接返回，避免执行无意义查询
            page.setRecords(new ArrayList<>());
            page.setPages(0L);
            return page;
        }

        // 总页数向上取整
        long pages = (total + pageSize - 1) / pageSize;
        page.setPages(pages);

        // 在原 Query 上叠加分页参数，查询当前页 Document 并转为 Map
        query.with(PageRequest.of(pageNum, pageSize, Sort.unsorted()));
        List<Document> records = mongoTemplate.find(query, Document.class, schema.getTableName());
        page.setRecords(documentsToMaps(records));

        return page;
    }

    /**
     * 批量保存记录（逐条调用 save）。
     * <p>
     * 每条记录独立处理 Map → Document 转换、自动填充与 upsert 判断。
     *
     * @param dataList 数据列表，为 null 或空时返回空列表
     * @return 保存后的数据列表
     */
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

    /**
     * 根据主键列表批量删除（使用 $in 一次性删除）。
     *
     * @param ids 主键列表，为 null 或空时不执行任何操作
     */
    @Override
    public void removeBatchByIds(List<Object> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Query query = new Query(Criteria.where(schema.getIdFieldName()).in(ids));
        mongoTemplate.remove(query, schema.getTableName());
    }

    /**
     * 根据主键列表批量查询（使用 $in 一次性查询）。
     *
     * @param ids 主键列表，为 null 或空时返回空列表
     * @return 匹配记录列表（Map 形式）
     */
    @Override
    public List<Map<String, Object>> listByIds(List<Object> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        Query query = new Query(Criteria.where(schema.getIdFieldName()).in(ids));
        List<Document> results = mongoTemplate.find(query, Document.class, schema.getTableName());
        // Document 列表 → Map 列表转换
        return documentsToMaps(results);
    }

    /**
     * 按条件统计记录数。
     *
     * @param queryParams 查询条件 Map，为 null 或空时统计全集合
     * @return 匹配的记录数
     */
    @Override
    public long count(Map<String, Object> queryParams) {
        Query query = buildQuery(queryParams);
        return mongoTemplate.count(query, schema.getTableName());
    }

    /**
     * 判断是否存在匹配条件的记录。
     *
     * @param queryParams 查询条件 Map
     * @return 存在返回 true，否则 false
     */
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
            // 仅 schema 内且非 null 的字段进入 Criteria，等值匹配
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
            // 仅处理与当前填充类型匹配的字段（CREATE / UPDATE / CREATE_UPDATE）
            if (field.getAutoFill() == fillType) {
                String name = field.getName();
                // 仅在字段未显式设置时填充，避免覆盖调用方传入的值
                if (!document.containsKey(name)) {
                    // 按字段类型生成对应的时间值：DATETIME 用 LocalDateTime，DATE 用 LocalDate
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
        // Document 本身即 Map 派生，此处拷贝为独立 HashMap 以隔离 MongoDB 驱动类型
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
        // 逐条 Document → Map 转换
        List<Map<String, Object>> result = new ArrayList<>();
        for (Document doc : documents) {
            result.add(documentToMap(doc));
        }
        return result;
    }
}