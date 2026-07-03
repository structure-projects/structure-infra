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
 * 基于 Spring Data Elasticsearch 的低代码存储实现，使用 {@code Map<String, Object>} 代替 POJO 操作文档，
 * 通过 {@link ElasticsearchOperations} 动态操作 ES 索引。
 * <p>
 * 核心特性：
 * <ul>
 *   <li>Map 动态操作：使用 Map 代替 POJO，无需定义实体类</li>
 *   <li>自动创建索引：初始化时自动创建索引（<b>不创建 mapping</b>，由 ES 动态映射字段类型）</li>
 *   <li>更新策略：先 delete 再 index（非部分更新），保证文档状态与入参一致</li>
 *   <li>索引定位：通过 {@link IndexCoordinates#of(String)} 以 schema.tableName 定位索引</li>
 *   <li>自动填充：支持创建时间、更新时间自动填充</li>
 *   <li>动态查询：根据查询条件动态构建 Elasticsearch 查询</li>
 *   <li>分页查询：支持分页查询，自动处理总数统计</li>
 *   <li>常作为 CQRS 读侧：适用于全文检索、聚合分析等读多写少场景</li>
 * </ul>
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@Slf4j
public class ElasticsearchLowCodeStorage implements LowCodeStorage {

    /** 资源 schema 定义（索引名、字段、主键等） */
    private final ResourceSchema schema;
    /** Elasticsearch 操作模板 */
    private final ElasticsearchOperations elasticsearchOperations;
    /** 索引坐标，由 schema.tableName 构建，用于定位 ES 索引 */
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
        // 通过 schema.tableName 构建 IndexCoordinates，后续所有操作均以此定位索引
        this.indexCoordinates = IndexCoordinates.of(schema.getTableName());
    }

    /**
     * 初始化存储结构：自动创建 ES 索引（不创建 mapping）。
     * <p>
     * 若索引不存在则调用 {@code indexOps.create()} 创建空索引，由 ES 在首次写入时动态映射字段类型；
     * 已存在则跳过。
     */
    @Override
    public void initialize() {
        String indexName = schema.getTableName();

        // 检查索引是否存在，不存在则创建（不创建 mapping，由 ES 动态映射）
        boolean indexExists = elasticsearchOperations.indexOps(indexCoordinates).exists();
        if (!indexExists) {
            elasticsearchOperations.indexOps(indexCoordinates).create();
            log.info("Elasticsearch index created: {}", indexName);
        }

        log.info("Elasticsearch lowcode storage initialized: {}", indexName);
    }

    /**
     * 保存或更新一条记录（以 Map 形式）。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>拷贝入参 Map，避免污染调用方</li>
     *   <li>按 {@link AutoFillType#CREATE} 与 {@link AutoFillType#CREATE_UPDATE} 自动填充时间字段</li>
     *   <li>根据主键是否存在且库中已有同 ID 文档，决定走 doUpdate 或 doIndex</li>
     * </ol>
     *
     * @param data 数据 Map，键为字段名、值为字段值
     * @return 保存后的数据（含自动填充字段）
     */
    @Override
    public Map<String, Object> save(Map<String, Object> data) {
        // 拷贝一份，避免污染调用方传入的 Map
        Map<String, Object> rowData = new HashMap<>(data);
        // 创建场景填充：CREATE_TIME 等
        fillAutoFields(rowData, AutoFillType.CREATE);
        // 创建/更新双重填充：CREATE_UPDATE 字段
        fillAutoFields(rowData, AutoFillType.CREATE_UPDATE);

        String idField = schema.getIdFieldName();
        Object idValue = rowData.get(idField);

        if (idValue != null) {
            // 已带主键时先查库，存在则更新、不存在则插入
            Map<String, Object> existing = findById(idValue);
            if (existing != null) {
                return doUpdate(rowData);
            }
        }

        return doIndex(rowData);
    }

    /**
     * 执行索引操作（新增或覆盖索引）。
     * <p>
     * 通过 {@link IndexQueryBuilder} 构建索引请求，主键值统一转换为 String 作为 ES 文档 ID。
     *
     * @param data 数据
     * @return 索引后的数据（含 ES 返回的文档 ID）
     */
    private Map<String, Object> doIndex(Map<String, Object> data) {
        String idField = schema.getIdFieldName();
        Object idValue = data.get(idField);

        // ID 统一转换为 String 作为 ES 文档 ID
        IndexQuery indexQuery = new IndexQueryBuilder()
                .withId(idValue != null ? String.valueOf(idValue) : null)
                .withObject(data)
                .build();

        // 执行索引操作，返回 ES 文档 ID
        String documentId = elasticsearchOperations.index(indexQuery, indexCoordinates);
        data.put(idField, documentId);

        return data;
    }

    /**
     * 执行更新操作。
     * <p>
     * 采用"先 delete 再 index"策略（非部分更新）：先按 ID 删除旧文档，再以入参完整索引新文档，
     * 保证文档状态与入参一致，避免部分更新遗漏字段。
     *
     * @param data 数据
     * @return 更新后的数据
     */
    private Map<String, Object> doUpdate(Map<String, Object> data) {
        String idField = schema.getIdFieldName();
        Object idValue = data.get(idField);

        // 步骤1：删除旧文档（ID 转 String）
        elasticsearchOperations.delete(String.valueOf(idValue), indexCoordinates);

        // 步骤2：以入参完整重新索引（非部分更新）
        return doIndex(data);
    }

    /**
     * 根据主键删除文档。
     * <p>
     * ID 统一转换为 String 作为 ES 文档 ID。
     *
     * @param id 主键值
     */
    @Override
    public void removeById(Object id) {
        elasticsearchOperations.delete(String.valueOf(id), indexCoordinates);
    }

    /**
     * 根据主键查询单条记录。
     * <p>
     * ID 统一转换为 String 作为 ES 文档 ID，结果以 Map 形式返回。
     *
     * @param id 主键值
     * @return 数据 Map，未找到时返回 null
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> findById(Object id) {
        Map<String, Object> result = elasticsearchOperations.get(String.valueOf(id), Map.class, indexCoordinates);
        return result;
    }

    /**
     * 根据主键查询（与 findById 等价，语义上用于"读模型"，常作为 CQRS 读侧）。
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
     * 仅 schema 中存在且非 null 的字段才会进入查询 Criteria。查询结果通过 SearchHit.getContent()
     * 提取为 Map 列表返回。
     *
     * @param queryParams 查询条件 Map，为 null 或空时匹配全部文档
     * @return 匹配记录列表，无匹配时返回空列表
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Map<String, Object>> queryList(Map<String, Object> queryParams) {
        Query query = buildQuery(queryParams);
        SearchHits<Map<String, Object>> searchHits = elasticsearchOperations.search(query,
                (Class<Map<String, Object>>) (Class<?>) Map.class, indexCoordinates);

        // 提取 SearchHit 的 content 为 Map 列表
        List<Map<String, Object>> results = new ArrayList<>();
        for (SearchHit<Map<String, Object>> hit : searchHits.getSearchHits()) {
            results.add(hit.getContent());
        }
        return results;
    }

    /**
     * 分页查询。
     * <p>
     * 通过 {@link PageRequest} 叠加分页参数，由 ES 原生分页（from/size）执行；
     * totalHits 为 0 时直接返回空记录。
     *
     * @param reqPage 分页请求（页码从 1 开始、每页大小，为 null 时取默认 1/10）
     * @return 分页结果，含当前页、总页数、总条数、当前页记录
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ResPage<Map<String, Object>> queryPage(ReqPage reqPage) {
        // ES 页码从 0 开始，业务页码从 1 开始，需减 1
        int pageNum = reqPage.getPage() != null ? reqPage.getPage().intValue() - 1 : 0;
        int pageSize = reqPage.getSize() != null ? reqPage.getSize().intValue() : 10;

        Query query = buildQuery(null);
        PageRequest pageRequest = PageRequest.of(pageNum, pageSize, Sort.unsorted());
        query.setPageable(pageRequest);

        SearchHits<Map<String, Object>> searchHits = elasticsearchOperations.search(query,
                (Class<Map<String, Object>>) (Class<?>) Map.class, indexCoordinates);

        ResPage<Map<String, Object>> page = new ResPage<>();
        // 返回业务侧时页码再加回 1
        page.setCurrent((long) pageNum + 1);
        page.setSize((long) pageSize);
        page.setTotal(searchHits.getTotalHits());

        if (searchHits.getTotalHits() == 0) {
            // 无数据时直接返回，避免提取空 SearchHits
            page.setRecords(new ArrayList<>());
            page.setPages(0L);
            return page;
        }

        // 总页数向上取整
        long pages = (searchHits.getTotalHits() + pageSize - 1) / pageSize;
        page.setPages(pages);

        // 提取当前页 SearchHit 的 content 为 Map 列表
        List<Map<String, Object>> records = new ArrayList<>();
        for (SearchHit<Map<String, Object>> hit : searchHits.getSearchHits()) {
            records.add(hit.getContent());
        }
        page.setRecords(records);

        return page;
    }

    /**
     * 批量保存记录（逐条调用 save）。
     * <p>
     * 每条记录独立处理 Map 拷贝、自动填充与 upsert 判断。
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
     * 根据主键列表批量删除（逐条 delete，ID 转 String）。
     *
     * @param ids 主键列表，为 null 或空时不执行任何操作
     */
    @Override
    public void removeBatchByIds(List<Object> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Object id : ids) {
            elasticsearchOperations.delete(String.valueOf(id), indexCoordinates);
        }
    }

    /**
     * 根据主键列表批量查询（逐条 get 并过滤 null）。
     *
     * @param ids 主键列表，为 null 或空时返回空列表
     * @return 匹配记录列表（Map 形式）
     */
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

    /**
     * 按条件统计记录数。
     *
     * @param queryParams 查询条件 Map，为 null 或空时统计全部文档
     * @return 匹配的记录数
     */
    @Override
    public long count(Map<String, Object> queryParams) {
        Query query = buildQuery(queryParams);
        return elasticsearchOperations.count(query, indexCoordinates);
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
     * 构建 Elasticsearch 查询
     *
     * @param queryParams 查询参数
     * @return Elasticsearch Query 对象
     */
    private Query buildQuery(Map<String, Object> queryParams) {
        Criteria criteria = new Criteria();

        if (queryParams != null && !queryParams.isEmpty()) {
            // 仅 schema 内且非 null 的字段进入 Criteria，等值匹配
            boolean first = true;
            for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
                String fieldName = entry.getKey();
                if (schema.getField(fieldName) != null && entry.getValue() != null) {
                    if (first) {
                        // 第一个条件用 Criteria.where 初始化
                        criteria = Criteria.where(fieldName).is(entry.getValue());
                        first = false;
                    } else {
                        // 后续条件用 and 链式拼接
                        criteria = criteria.and(Criteria.where(fieldName).is(entry.getValue()));
                    }
                }
            }
        } else {
            // 无查询条件时匹配全部文档（通过 _id exists 兜底）
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
            // 仅处理与当前填充类型匹配的字段（CREATE / UPDATE / CREATE_UPDATE）
            if (field.getAutoFill() == fillType) {
                String name = field.getName();
                // 仅在字段未显式设置时填充，避免覆盖调用方传入的值
                if (!data.containsKey(name)) {
                    // 按字段类型生成对应的时间值：DATETIME 用 LocalDateTime，DATE 用 LocalDate
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