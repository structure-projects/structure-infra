package cn.structure.infra.sample.mongodb.lowcode;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.lowcode.model.*;
import cn.structure.infra.lowcode.repository.LowCodeRepository;
import cn.structure.infra.lowcode.router.LowCodeRepositoryRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * MongoDB 低代码仓储测试 - 通过 LowCodeRepository 接口测试
 * <p>
 * 验证 LowCodeRepositoryRouter 和 MongoDB 低代码存储的完整集成。
 *
 * @author chuck
 * @since 2026/6/29
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MongoDB 低代码仓储测试 - LowCodeRepository 接口")
class MongoLowCodeRepositoryTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private MongoConverter mongoConverter;

    private LowCodeRepository lowCodeRepository;

    private final Map<String, Map<Object, org.bson.Document>> docCollections = new LinkedHashMap<>();
    private final AtomicLong docIdGenerator = new AtomicLong(1);

    private static final String RESOURCE_NAME = "article";
    private static final String COLLECTION_NAME = "t_lowcode_article";

    @BeforeEach
    void setUp() {
        docCollections.clear();
        docIdGenerator.set(1);
        setupMock();

        ResourceSchema schema = ResourceSchema.builder()
                .resourceName(RESOURCE_NAME)
                .tableName(COLLECTION_NAME)
                .build();

        schema.addField(FieldSchema.builder()
                .name("id")
                .type(FieldType.LONG)
                .primaryKey(true)
                .build());

        schema.addField(FieldSchema.builder()
                .name("title")
                .type(FieldType.STRING)
                .length(200)
                .nullable(false)
                .build());

        schema.addField(FieldSchema.builder()
                .name("content")
                .type(FieldType.TEXT)
                .nullable(false)
                .build());

        schema.addField(FieldSchema.builder()
                .name("author")
                .type(FieldType.STRING)
                .length(50)
                .nullable(false)
                .index(true)
                .build());

        schema.addField(FieldSchema.builder()
                .name("category")
                .type(FieldType.STRING)
                .length(50)
                .index(true)
                .build());

        schema.addField(FieldSchema.builder()
                .name("viewCount")
                .type(FieldType.LONG)
                .defaultValue("0")
                .build());

        schema.addField(FieldSchema.builder()
                .name("status")
                .type(FieldType.INTEGER)
                .defaultValue("1")
                .build());

        schema.addField(FieldSchema.builder()
                .name("created_at")
                .type(FieldType.DATETIME)
                .autoFill(AutoFillType.CREATE)
                .build());

        schema.addField(FieldSchema.builder()
                .name("updated_at")
                .type(FieldType.DATETIME)
                .autoFill(AutoFillType.CREATE_UPDATE)
                .build());

        RepositoryConfig config = new RepositoryConfig();
        config.setType(StorageType.MONGODB);

        cn.structure.infra.lowcode.repository.LowCodeRepoFactory factory =
                new cn.structure.infra.mongodb.lowcode.MongoLowCodeRepoFactory(mongoTemplate);

        List<cn.structure.infra.lowcode.repository.LowCodeRepoFactory> factories = new ArrayList<>();
        factories.add(factory);

        LowCodeRepositoryRouter router = new LowCodeRepositoryRouter(factories);
        router.registerResource(RESOURCE_NAME, schema, config);
        lowCodeRepository = router;
    }

    @SuppressWarnings("unchecked")
    private void setupMock() {
        when(mongoTemplate.getConverter()).thenReturn(mongoConverter);

        // Mock indexOps
        org.springframework.data.mongodb.core.index.IndexOperations mockIndexOps = mock(org.springframework.data.mongodb.core.index.IndexOperations.class);
        when(mongoTemplate.indexOps(anyString())).thenReturn(mockIndexOps);
        when(mongoTemplate.indexOps(any(Class.class))).thenReturn(mockIndexOps);

        when(mongoTemplate.collectionExists(anyString())).thenReturn(true);

        doAnswer(invocation -> {
            String collectionName = invocation.getArgument(0);
            docCollections.remove(collectionName);
            return null;
        }).when(mongoTemplate).dropCollection(anyString());

        when(mongoTemplate.findOne(any(Query.class), any(Class.class), anyString())).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            String collectionName = invocation.getArgument(2);
            Map<Object, org.bson.Document> collection = docCollections.get(collectionName);
            if (collection == null || collection.isEmpty()) {
                return null;
            }
            List<org.bson.Document> allDocs = new ArrayList<>(collection.values());
            List<org.bson.Document> filtered = filterDocsByQuery(query, allDocs);
            return filtered.isEmpty() ? null : filtered.get(0);
        });

        when(mongoTemplate.find(any(Query.class), any(Class.class), anyString())).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            String collectionName = invocation.getArgument(2);
            Map<Object, org.bson.Document> collection = docCollections.get(collectionName);
            if (collection == null || collection.isEmpty()) {
                return new ArrayList<>();
            }
            List<org.bson.Document> allDocs = new ArrayList<>(collection.values());
            List<org.bson.Document> filtered = filterDocsByQuery(query, allDocs);

            long skip = query.getSkip();
            int limit = query.getLimit();

            List<org.bson.Document> result = new ArrayList<>();
            int start = (int) skip;
            int end = limit > 0 ? Math.min(start + limit, filtered.size()) : filtered.size();
            if (start < filtered.size()) {
                result.addAll(filtered.subList(start, end));
            }
            return result;
        });

        when(mongoTemplate.insert(any(org.bson.Document.class), anyString())).thenAnswer(invocation -> {
            org.bson.Document doc = invocation.getArgument(0);
            String collectionName = invocation.getArgument(1);
            Map<Object, org.bson.Document> collection = docCollections.computeIfAbsent(collectionName, k -> new LinkedHashMap<>());

            String idField = "id";
            if (!doc.containsKey(idField) || doc.get(idField) == null) {
                doc.put(idField, docIdGenerator.getAndIncrement());
            }
            Object id = doc.get(idField);
            collection.put(id, doc);
            return doc;
        });

        when(mongoTemplate.save(any(org.bson.Document.class), anyString())).thenAnswer(invocation -> {
            org.bson.Document doc = invocation.getArgument(0);
            String collectionName = invocation.getArgument(1);
            Map<Object, org.bson.Document> collection = docCollections.computeIfAbsent(collectionName, k -> new LinkedHashMap<>());

            String idField = "id";
            if (!doc.containsKey(idField) || doc.get(idField) == null) {
                doc.put(idField, docIdGenerator.getAndIncrement());
            }
            Object id = doc.get(idField);
            collection.put(id, doc);
            return doc;
        });

        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), anyString())).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            String collectionName = invocation.getArgument(2);
            Map<Object, org.bson.Document> collection = docCollections.get(collectionName);
            if (collection == null || collection.isEmpty()) {
                return null;
            }
            List<org.bson.Document> allDocs = new ArrayList<>(collection.values());
            List<org.bson.Document> filtered = filterDocsByQuery(query, allDocs);
            if (!filtered.isEmpty()) {
                org.bson.Document firstDoc = filtered.get(0);
                Update update = invocation.getArgument(1);
                Map<String, Object> updates = extractUpdateValues(update);
                firstDoc.putAll(updates);
            }
            return null;
        });

        doAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            String collectionName = invocation.getArgument(1);
            Map<Object, org.bson.Document> collection = docCollections.get(collectionName);
            if (collection == null || collection.isEmpty()) {
                return null;
            }
            List<org.bson.Document> allDocs = new ArrayList<>(collection.values());
            List<org.bson.Document> filtered = filterDocsByQuery(query, allDocs);
            for (org.bson.Document doc : filtered) {
                Object id = doc.get("id");
                if (id != null) {
                    collection.remove(id);
                }
            }
            return null;
        }).when(mongoTemplate).remove(any(Query.class), anyString());

        when(mongoTemplate.count(any(Query.class), anyString())).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            String collectionName = invocation.getArgument(1);
            Map<Object, org.bson.Document> collection = docCollections.get(collectionName);
            if (collection == null || collection.isEmpty()) {
                return 0L;
            }
            List<org.bson.Document> allDocs = new ArrayList<>(collection.values());
            List<org.bson.Document> filtered = filterDocsByQuery(query, allDocs);
            return (long) filtered.size();
        });
    }

    private List<org.bson.Document> filterDocsByQuery(Query query, List<org.bson.Document> docs) {
        try {
            Field criteriaField = Query.class.getDeclaredField("criteria");
            criteriaField.setAccessible(true);
            Object criteriaObj = criteriaField.get(query);

            if (criteriaObj instanceof Map) {
                Map<?, ?> criteriaMap = (Map<?, ?>) criteriaObj;
                List<Map.Entry<String, Object>> conditions = new ArrayList<>();
                for (Map.Entry<?, ?> entry : criteriaMap.entrySet()) {
                    String fieldName = entry.getKey().toString();
                    Object criteriaValue = entry.getValue();
                    if (criteriaValue instanceof Criteria) {
                        Object value = extractCriteriaValue((Criteria) criteriaValue);
                        if (value != null) {
                            conditions.add(Map.entry(fieldName, value));
                        }
                    }
                }
                if (!conditions.isEmpty()) {
                    return docs.stream()
                            .filter(doc -> matchesConditions(doc, conditions))
                            .collect(java.util.stream.Collectors.toList());
                }
            }
        } catch (Exception ignored) {
        }
        return docs;
    }

    private Object extractCriteriaValue(Criteria criteria) {
        try {
            Field isValueField = Criteria.class.getDeclaredField("isValue");
            isValueField.setAccessible(true);
            Object value = isValueField.get(criteria);
            if (value != null && !"java.lang.Object".equals(value.getClass().getName())) {
                return value;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean matchesConditions(org.bson.Document doc, List<Map.Entry<String, Object>> conditions) {
        for (Map.Entry<String, Object> condition : conditions) {
            String fieldName = condition.getKey();
            Object expectedValue = condition.getValue();
            Object actualValue = doc.get(fieldName);
            if (actualValue == null || !actualValue.equals(expectedValue)) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> extractUpdateValues(Update update) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Field modifierOpsField = Update.class.getDeclaredField("modifierOps");
            modifierOpsField.setAccessible(true);
            Object modifierOps = modifierOpsField.get(update);
            if (modifierOps instanceof Map) {
                Map<?, ?> opsMap = (Map<?, ?>) modifierOps;
                Object setDoc = opsMap.get("$set");
                if (setDoc instanceof org.bson.Document) {
                    result.putAll((org.bson.Document) setDoc);
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private Map<String, Object> documentToMap(org.bson.Document doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (doc != null) {
            for (String key : doc.keySet()) {
                map.put(key, doc.get(key));
            }
        }
        return map;
    }

    // ==================== 测试方法 ====================

    @Test
    @DisplayName("测试保存文章（新增）- LowCodeRepository")
    void testSave_Insert() {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Test Article Title");
        article.put("content", "This is test article content.");
        article.put("author", "test_author");
        article.put("category", "tech");
        article.put("viewCount", 100L);
        article.put("status", 1);

        Map<String, Object> result = lowCodeRepository.save(RESOURCE_NAME, article);

        assertNotNull(result);
        assertNotNull(result.get("id"), "ID 应该自动生成");
        assertEquals("Test Article Title", result.get("title"));
        assertEquals("test_author", result.get("author"));
        assertNotNull(result.get("created_at"), "应该自动填充创建时间");
        assertNotNull(result.get("updated_at"), "应该自动填充更新时间");
    }

    @Test
    @DisplayName("测试保存文章（更新）- LowCodeRepository")
    void testSave_Update() {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Original Title");
        article.put("content", "Original content");
        article.put("author", "original_author");
        article.put("category", "news");

        Map<String, Object> saved = lowCodeRepository.save(RESOURCE_NAME, article);
        Object id = saved.get("id");

        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("id", id);
        updateData.put("title", "Updated Title");
        updateData.put("viewCount", 200L);

        Map<String, Object> updated = lowCodeRepository.save(RESOURCE_NAME, updateData);

        assertEquals(id, updated.get("id"));
        assertEquals("Updated Title", updated.get("title"));
        assertEquals(200L, updated.get("viewCount"));
    }

    @Test
    @DisplayName("测试根据ID查询 - LowCodeRepository")
    void testFindById() {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Find By Id Test");
        article.put("content", "Content for find by id test");
        article.put("author", "test_author");

        Map<String, Object> saved = lowCodeRepository.save(RESOURCE_NAME, article);
        Object id = saved.get("id");

        Map<String, Object> result = lowCodeRepository.findById(RESOURCE_NAME, id);

        assertNotNull(result);
        assertEquals(id, result.get("id"));
        assertEquals("Find By Id Test", result.get("title"));
    }

    @Test
    @DisplayName("测试条件查询单条 - LowCodeRepository")
    void testQueryOne() {
        Map<String, Object> article1 = new LinkedHashMap<>();
        article1.put("title", "Article One");
        article1.put("content", "Content one");
        article1.put("author", "author_1");
        article1.put("category", "tech");
        lowCodeRepository.save(RESOURCE_NAME, article1);

        Map<String, Object> article2 = new LinkedHashMap<>();
        article2.put("title", "Article Two");
        article2.put("content", "Content two");
        article2.put("author", "author_2");
        article2.put("category", "news");
        lowCodeRepository.save(RESOURCE_NAME, article2);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "tech");

        Map<String, Object> result = lowCodeRepository.queryOne(RESOURCE_NAME, params);

        assertNotNull(result);
        assertEquals("Article One", result.get("title"));
        assertEquals("tech", result.get("category"));
    }

    @Test
    @DisplayName("测试条件查询列表 - LowCodeRepository")
    void testQueryList() {
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "Tech Article " + i);
            article.put("content", "Tech content " + i);
            article.put("author", "tech_author");
            article.put("category", i <= 3 ? "tech" : "news");
            lowCodeRepository.save(RESOURCE_NAME, article);
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "tech");

        List<Map<String, Object>> results = lowCodeRepository.queryList(RESOURCE_NAME, params);

        assertNotNull(results);
        assertEquals(3, results.size());
    }

    @Test
    @DisplayName("测试分页查询 - LowCodeRepository")
    void testQueryPage() {
        for (int i = 1; i <= 25; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "Page Article " + i);
            article.put("content", "Page content " + i);
            article.put("author", "page_author");
            article.put("category", "page_category");
            lowCodeRepository.save(RESOURCE_NAME, article);
        }

        ReqPage reqPage = new ReqPage();
        reqPage.setPage(1);
        reqPage.setSize(10);
        ResPage<Map<String, Object>> result = lowCodeRepository.queryPage(RESOURCE_NAME, reqPage);

        assertNotNull(result);
        assertEquals(25L, result.getTotal());
        assertEquals(10, result.getRecords().size());
    }

    @Test
    @DisplayName("测试删除文章 - LowCodeRepository")
    void testRemoveById() {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Delete Test Article");
        article.put("content", "Content to delete");
        article.put("author", "delete_author");

        Map<String, Object> saved = lowCodeRepository.save(RESOURCE_NAME, article);
        Object id = saved.get("id");

        lowCodeRepository.removeById(RESOURCE_NAME, id);

        Map<String, Object> result = lowCodeRepository.findById(RESOURCE_NAME, id);
        assertNull(result);
    }

    @Test
    @DisplayName("测试批量保存 - LowCodeRepository")
    void testSaveBatch() {
        List<Map<String, Object>> articles = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "Batch Article " + i);
            article.put("content", "Batch content " + i);
            article.put("author", "batch_author");
            articles.add(article);
        }

        List<Map<String, Object>> results = lowCodeRepository.saveBatch(RESOURCE_NAME, articles);

        assertNotNull(results);
        assertEquals(5, results.size());
        for (Map<String, Object> r : results) {
            assertNotNull(r.get("id"));
        }
    }

    @Test
    @DisplayName("测试批量删除 - LowCodeRepository")
    void testRemoveBatchByIds() {
        List<Object> ids = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "Batch Remove " + i);
            article.put("content", "Content " + i);
            article.put("author", "batch_remove_author");
            Map<String, Object> saved = lowCodeRepository.save(RESOURCE_NAME, article);
            ids.add(saved.get("id"));
        }

        lowCodeRepository.removeBatchByIds(RESOURCE_NAME, ids);

        for (Object id : ids) {
            Map<String, Object> result = lowCodeRepository.findById(RESOURCE_NAME, id);
            assertNull(result);
        }
    }

    @Test
    @DisplayName("测试批量查询 - LowCodeRepository")
    void testListByIds() {
        List<Object> ids = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "List By Ids " + i);
            article.put("content", "Content " + i);
            article.put("author", "list_author");
            Map<String, Object> saved = lowCodeRepository.save(RESOURCE_NAME, article);
            ids.add(saved.get("id"));
        }

        List<Map<String, Object>> results = lowCodeRepository.listByIds(RESOURCE_NAME, ids);

        assertNotNull(results);
        assertEquals(3, results.size());
    }

    @Test
    @DisplayName("测试统计数量 - LowCodeRepository")
    void testCount() {
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "Count Article " + i);
            article.put("content", "Count content " + i);
            article.put("author", "count_author");
            article.put("category", i <= 3 ? "tech" : "news");
            lowCodeRepository.save(RESOURCE_NAME, article);
        }

        long count = lowCodeRepository.count(RESOURCE_NAME, null);
        assertEquals(5, count);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "tech");
        long conditionCount = lowCodeRepository.count(RESOURCE_NAME, params);
        assertEquals(3, conditionCount);
    }

    @Test
    @DisplayName("测试判断存在 - LowCodeRepository")
    void testExists() {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Exists Test Article");
        article.put("content", "Content for exists test");
        article.put("author", "exists_author");
        article.put("category", "exists_category");
        lowCodeRepository.save(RESOURCE_NAME, article);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("author", "exists_author");
        boolean exists = lowCodeRepository.exists(RESOURCE_NAME, params);
        assertTrue(exists);

        params.put("author", "not_exists_author");
        boolean notExists = lowCodeRepository.exists(RESOURCE_NAME, params);
        assertFalse(notExists);
    }

    @Test
    @DisplayName("测试自动填充时间字段 - LowCodeRepository")
    void testAutoFill() {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "AutoFill Test Article");
        article.put("content", "Content for auto fill test");
        article.put("author", "autofill_author");

        Map<String, Object> result = lowCodeRepository.save(RESOURCE_NAME, article);

        assertNotNull(result.get("created_at"));
        assertNotNull(result.get("updated_at"));
    }

    @Test
    @DisplayName("测试 queryById - LowCodeRepository")
    void testQueryById() {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "QueryById Test");
        article.put("content", "Content for query by id test");
        article.put("author", "query_author");

        Map<String, Object> saved = lowCodeRepository.save(RESOURCE_NAME, article);
        Object id = saved.get("id");

        Map<String, Object> result = lowCodeRepository.queryById(RESOURCE_NAME, id);

        assertNotNull(result);
        assertEquals(id, result.get("id"));
        assertEquals("QueryById Test", result.get("title"));
    }

    @Test
    @DisplayName("测试 queryByIdOptional - LowCodeRepository")
    void testQueryByIdOptional() {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Optional Test");
        article.put("content", "Content for optional test");
        article.put("author", "optional_author");

        Map<String, Object> saved = lowCodeRepository.save(RESOURCE_NAME, article);
        Object id = saved.get("id");

        Optional<Map<String, Object>> result = lowCodeRepository.queryByIdOptional(RESOURCE_NAME, id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().get("id"));
    }

    @Test
    @DisplayName("测试 queryOneOptional - LowCodeRepository")
    void testQueryOneOptional() {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "QueryOneOptional Test");
        article.put("content", "Content");
        article.put("author", "qopt_author");
        article.put("category", "qopt_cat");
        lowCodeRepository.save(RESOURCE_NAME, article);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "qopt_cat");

        Optional<Map<String, Object>> result = lowCodeRepository.queryOneOptional(RESOURCE_NAME, params);

        assertTrue(result.isPresent());
        assertEquals("QueryOneOptional Test", result.get().get("title"));
    }
}
