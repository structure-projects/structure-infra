package cn.structure.infra.sample.mongodb.lowcode;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.lowcode.model.AutoFillType;
import cn.structure.infra.lowcode.model.FieldSchema;
import cn.structure.infra.lowcode.model.FieldType;
import cn.structure.infra.lowcode.model.ResourceSchema;
import cn.structure.infra.lowcode.repository.LowCodeStorage;
import cn.structure.infra.mongodb.lowcode.MongoLowCodeStorage;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * MongoDB 低代码仓储单元测试
 * <p>
 * 使用 Mockito 直接模拟 MongoTemplate，验证 MongoLowCodeStorage 的各项功能。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MongoDB 低代码仓储单元测试")
class MongoLowCodeRepositoryTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private IndexOperations indexOperations;

    @Mock
    private MongoConverter mongoConverter;

    private LowCodeStorage storage;

    private final Map<String, Map<Object, Document>> docCollections = new ConcurrentHashMap<>();
    private final AtomicLong docIdGenerator = new AtomicLong(1);

    private static final String COLLECTION_NAME = "t_lowcode_user";

    @BeforeEach
    void setUp() {
        docCollections.clear();
        docIdGenerator.set(1);

        setupMockMongoTemplate();

        ResourceSchema schema = buildUserSchema();
        storage = new MongoLowCodeStorage(schema, mongoTemplate);
        storage.initialize();
    }

    private ResourceSchema buildUserSchema() {
        ResourceSchema schema = ResourceSchema.builder()
                .resourceName("user")
                .tableName(COLLECTION_NAME)
                .build();

        schema.addField(FieldSchema.builder()
                .name("id")
                .type(FieldType.LONG)
                .primaryKey(true)
                .autoIncrement(true)
                .build());

        schema.addField(FieldSchema.builder()
                .name("username")
                .type(FieldType.STRING)
                .length(50)
                .nullable(false)
                .unique(true)
                .index(true)
                .build());

        schema.addField(FieldSchema.builder()
                .name("email")
                .type(FieldType.STRING)
                .length(100)
                .nullable(false)
                .build());

        schema.addField(FieldSchema.builder()
                .name("age")
                .type(FieldType.INTEGER)
                .nullable(true)
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

        return schema;
    }

    private void setupMockMongoTemplate() {
        // collectionExists
        when(mongoTemplate.collectionExists(anyString())).thenAnswer(invocation -> {
            String collectionName = invocation.getArgument(0);
            return docCollections.containsKey(collectionName);
        });

        // createCollection
        doAnswer(invocation -> {
            String collectionName = invocation.getArgument(0);
            docCollections.putIfAbsent(collectionName, new LinkedHashMap<>());
            return null;
        }).when(mongoTemplate).createCollection(anyString());

        // dropCollection
        doAnswer(invocation -> {
            String collectionName = invocation.getArgument(0);
            docCollections.remove(collectionName);
            return null;
        }).when(mongoTemplate).dropCollection(anyString());

        // indexOps
        when(indexOperations.ensureIndex(any(Index.class))).thenReturn("");
        when(mongoTemplate.indexOps(anyString())).thenReturn(indexOperations);

        // findOne with collectionName
        when(mongoTemplate.findOne(any(Query.class), any(Class.class), anyString())).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            String collectionName = invocation.getArgument(2);
            Map<Object, Document> collection = docCollections.get(collectionName);
            if (collection == null || collection.isEmpty()) {
                return null;
            }
            List<Document> allDocs = new ArrayList<>(collection.values());
            List<Document> filtered = filterDocsByQuery(query, allDocs);
            return filtered.isEmpty() ? null : filtered.get(0);
        });

        // find with collectionName
        when(mongoTemplate.find(any(Query.class), any(Class.class), anyString())).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            String collectionName = invocation.getArgument(2);
            Map<Object, Document> collection = docCollections.get(collectionName);
            if (collection == null || collection.isEmpty()) {
                return new ArrayList<Document>();
            }
            List<Document> allDocs = new ArrayList<>(collection.values());
            return filterDocsByQuery(query, allDocs);
        });

        // insert with collectionName
        when(mongoTemplate.insert(any(Document.class), anyString())).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            String collectionName = invocation.getArgument(1);
            Map<Object, Document> collection = docCollections.computeIfAbsent(collectionName, k -> new LinkedHashMap<>());

            String idField = "id";
            if (!doc.containsKey(idField) || doc.get(idField) == null) {
                doc.put(idField, docIdGenerator.getAndIncrement());
            }
            Object id = doc.get(idField);
            collection.put(id, doc);
            return doc;
        });

        // save with collectionName
        when(mongoTemplate.save(any(Document.class), anyString())).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            String collectionName = invocation.getArgument(1);
            Map<Object, Document> collection = docCollections.computeIfAbsent(collectionName, k -> new LinkedHashMap<>());

            String idField = "id";
            if (!doc.containsKey(idField) || doc.get(idField) == null) {
                doc.put(idField, docIdGenerator.getAndIncrement());
            }
            Object id = doc.get(idField);
            collection.put(id, doc);
            return doc;
        });

        // updateFirst with collectionName
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), anyString())).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            String collectionName = invocation.getArgument(2);
            Map<Object, Document> collection = docCollections.get(collectionName);
            if (collection == null || collection.isEmpty()) {
                return null;
            }
            List<Document> allDocs = new ArrayList<>(collection.values());
            List<Document> filtered = filterDocsByQuery(query, allDocs);
            if (!filtered.isEmpty()) {
                Document firstDoc = filtered.get(0);
                Update update = invocation.getArgument(1);
                Map<String, Object> updates = extractUpdateValues(update);
                firstDoc.putAll(updates);
            }
            return null;
        });

        // remove with query and collectionName
        doAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            String collectionName = invocation.getArgument(1);
            Map<Object, Document> collection = docCollections.get(collectionName);
            if (collection == null || collection.isEmpty()) {
                return null;
            }
            List<Document> allDocs = new ArrayList<>(collection.values());
            List<Document> filtered = filterDocsByQuery(query, allDocs);
            for (Document doc : filtered) {
                Object id = doc.get("id");
                if (id != null) {
                    collection.remove(id);
                }
            }
            return null;
        }).when(mongoTemplate).remove(any(Query.class), anyString());

        // count with collectionName
        when(mongoTemplate.count(any(Query.class), anyString())).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            String collectionName = invocation.getArgument(1);
            Map<Object, Document> collection = docCollections.get(collectionName);
            if (collection == null || collection.isEmpty()) {
                return 0L;
            }
            List<Document> allDocs = new ArrayList<>(collection.values());
            List<Document> filtered = filterDocsByQuery(query, allDocs);
            return (long) filtered.size();
        });

        // getConverter
        when(mongoTemplate.getConverter()).thenReturn(mongoConverter);
    }

    private List<Document> filterDocsByQuery(Query query, List<Document> docs) {
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
                            .collect(Collectors.toList());
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

    private boolean matchesConditions(Document doc, List<Map.Entry<String, Object>> conditions) {
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
            Field updatesField = Update.class.getDeclaredField("updates");
            updatesField.setAccessible(true);
            Object updates = updatesField.get(update);
            if (updates instanceof List) {
                for (Object u : (List<?>) updates) {
                    try {
                        Field keyField = u.getClass().getDeclaredField("key");
                        Field valueField = u.getClass().getDeclaredField("value");
                        keyField.setAccessible(true);
                        valueField.setAccessible(true);
                        String key = (String) keyField.get(u);
                        Object value = valueField.get(u);
                        result.put(key, value);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    @Test
    @DisplayName("测试保存用户（新增）")
    void testSave_Insert() {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("username", "test_user_insert");
        user.put("email", "test_insert@example.com");
        user.put("age", 25);
        user.put("status", 1);

        Map<String, Object> result = storage.save(user);

        assertNotNull(result);
        assertNotNull(result.get("id"), "ID 应该自动生成");
        assertEquals("test_user_insert", result.get("username"));
        assertEquals("test_insert@example.com", result.get("email"));
        assertEquals(25, result.get("age"));
        assertNotNull(result.get("created_at"), "应该自动填充创建时间");
        assertNotNull(result.get("updated_at"), "应该自动填充更新时间");
    }

    @Test
    @DisplayName("测试保存用户（更新）")
    void testSave_Update() {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("username", "test_user_update");
        user.put("email", "update@example.com");
        Map<String, Object> saved = storage.save(user);
        Object id = saved.get("id");

        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("id", id);
        updateData.put("username", "test_user_updated");
        updateData.put("email", "updated@example.com");
        Map<String, Object> updated = storage.save(updateData);

        assertNotNull(updated);
        assertEquals(id, updated.get("id"));
        assertEquals("test_user_updated", updated.get("username"));
        assertEquals("updated@example.com", updated.get("email"));
    }

    @Test
    @DisplayName("测试根据ID查询")
    void testFindById() {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("username", "find_by_id");
        user.put("email", "findbyid@example.com");
        Map<String, Object> saved = storage.save(user);
        Object id = saved.get("id");

        Map<String, Object> found = storage.findById(id);

        assertNotNull(found);
        assertEquals(id, found.get("id"));
        assertEquals("find_by_id", found.get("username"));
    }

    @Test
    @DisplayName("测试条件查询单个")
    void testQueryOne() {
        Map<String, Object> user1 = new LinkedHashMap<>();
        user1.put("username", "query_one_user1");
        user1.put("email", "user1@example.com");
        user1.put("status", 1);
        storage.save(user1);

        Map<String, Object> user2 = new LinkedHashMap<>();
        user2.put("username", "query_one_user2");
        user2.put("email", "user2@example.com");
        user2.put("status", 1);
        storage.save(user2);

        Map<String, Object> queryParams = new LinkedHashMap<>();
        queryParams.put("username", "query_one_user1");
        Map<String, Object> result = storage.queryOne(queryParams);

        assertNotNull(result);
        assertEquals("query_one_user1", result.get("username"));
    }

    @Test
    @DisplayName("测试条件查询列表")
    void testQueryList() {
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("username", "list_user_" + i);
            user.put("email", "list_" + i + "@example.com");
            user.put("status", i <= 3 ? 1 : 0);
            storage.save(user);
        }

        Map<String, Object> queryParams = new LinkedHashMap<>();
        queryParams.put("status", 1);
        List<Map<String, Object>> results = storage.queryList(queryParams);

        assertNotNull(results);
        assertEquals(3, results.size());
    }

    @Test
    @DisplayName("测试分页查询")
    void testQueryPage() {
        for (int i = 1; i <= 25; i++) {
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("username", "page_user_" + i);
            user.put("email", "page_" + i + "@example.com");
            user.put("status", 1);
            storage.save(user);
        }

        ReqPage reqPage = new ReqPage();
        reqPage.setPage(1);
        reqPage.setSize(10);
        ResPage<Map<String, Object>> result = storage.queryPage(reqPage);

        assertNotNull(result);
        assertEquals(25L, result.getTotal());
        assertEquals(10, result.getRecords().size());
    }

    @Test
    @DisplayName("测试批量保存")
    void testSaveBatch() {
        List<Map<String, Object>> users = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("username", "batch_user_" + i);
            user.put("email", "batch_" + i + "@example.com");
            users.add(user);
        }

        List<Map<String, Object>> saved = storage.saveBatch(users);

        assertNotNull(saved);
        assertEquals(5, saved.size());
        for (Map<String, Object> user : saved) {
            assertNotNull(user.get("id"), "每个用户都应该有 ID");
        }
    }

    @Test
    @DisplayName("测试根据ID删除")
    void testRemoveById() {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("username", "remove_by_id");
        user.put("email", "remove@example.com");
        Map<String, Object> saved = storage.save(user);
        Object id = saved.get("id");

        storage.removeById(id);

        assertNull(storage.findById(id));
    }

    @Test
    @DisplayName("测试批量删除")
    void testRemoveBatchByIds() {
        List<Object> ids = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("username", "batch_remove_" + i);
            user.put("email", "batch_remove_" + i + "@example.com");
            Map<String, Object> saved = storage.save(user);
            ids.add(saved.get("id"));
        }

        storage.removeBatchByIds(ids);

        for (Object id : ids) {
            assertNull(storage.findById(id));
        }
    }

    @Test
    @DisplayName("测试批量查询")
    void testListByIds() {
        List<Object> ids = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("username", "list_by_ids_" + i);
            user.put("email", "list_by_ids_" + i + "@example.com");
            Map<String, Object> saved = storage.save(user);
            ids.add(saved.get("id"));
        }

        List<Map<String, Object>> results = storage.listByIds(ids);

        assertNotNull(results);
        assertEquals(3, results.size());
    }

    @Test
    @DisplayName("测试统计数量")
    void testCount() {
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("username", "count_user_" + i);
            user.put("email", "count_" + i + "@example.com");
            user.put("status", i <= 3 ? 1 : 0);
            storage.save(user);
        }

        Map<String, Object> queryParams = new LinkedHashMap<>();
        queryParams.put("status", 1);
        long count = storage.count(queryParams);

        assertEquals(3, count);
    }

    @Test
    @DisplayName("测试自动填充")
    void testAutoFill() {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("username", "autofill_user");
        user.put("email", "autofill@example.com");

        Map<String, Object> saved = storage.save(user);

        assertNotNull(saved.get("created_at"), "创建时间应该自动填充");
        assertNotNull(saved.get("updated_at"), "更新时间应该自动填充");
    }

    @Test
    @DisplayName("测试存在性检查")
    void testExists() {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("username", "exists_user");
        user.put("email", "exists@example.com");
        storage.save(user);

        Map<String, Object> queryParams = new LinkedHashMap<>();
        queryParams.put("username", "exists_user");
        assertTrue(storage.exists(queryParams));

        queryParams.put("username", "nonexistent");
        assertFalse(storage.exists(queryParams));
    }
}
