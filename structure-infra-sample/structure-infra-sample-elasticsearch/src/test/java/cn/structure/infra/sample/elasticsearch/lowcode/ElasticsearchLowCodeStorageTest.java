package cn.structure.infra.sample.elasticsearch.lowcode;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.elasticsearch.lowcode.ElasticsearchLowCodeStorage;
import cn.structure.infra.lowcode.model.AutoFillType;
import cn.structure.infra.lowcode.model.FieldSchema;
import cn.structure.infra.lowcode.model.FieldType;
import cn.structure.infra.lowcode.model.ResourceSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.Query;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Elasticsearch 低代码仓储单元测试")
class ElasticsearchLowCodeStorageTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private IndexOperations indexOperations;

    private ElasticsearchLowCodeStorage storage;

    private final Map<String, Map<String, Object>> docStore = new LinkedHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    private static final String INDEX_NAME = "t_lowcode_article";

    @BeforeEach
    void setUp() {
        ResourceSchema schema = ResourceSchema.builder()
                .resourceName("article")
                .tableName(INDEX_NAME)
                .build();

        schema.addField(FieldSchema.builder()
                .name("id")
                .type(FieldType.STRING)
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

        storage = new ElasticsearchLowCodeStorage(schema, elasticsearchOperations);
        setupMock();
    }

    @SuppressWarnings("unchecked")
    private void setupMock() {
        docStore.clear();
        idGenerator.set(1);

        when(elasticsearchOperations.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);

        when(elasticsearchOperations.index(any(IndexQuery.class), any(IndexCoordinates.class))).thenAnswer(invocation -> {
            IndexQuery indexQuery = invocation.getArgument(0);
            String id = indexQuery.getId();
            Object object = indexQuery.getObject();

            if (object instanceof Map) {
                Map<String, Object> doc = new LinkedHashMap<>((Map<String, Object>) object);
                if (id == null || id.isEmpty()) {
                    id = String.valueOf(idGenerator.getAndIncrement());
                }
                doc.put("id", id);
                docStore.put(id, doc);
                return id;
            }
            return id != null ? id : String.valueOf(idGenerator.getAndIncrement());
        });

        when(elasticsearchOperations.get(anyString(), any(Class.class), any(IndexCoordinates.class))).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            Map<String, Object> doc = docStore.get(id);
            if (doc != null) {
                return new LinkedHashMap<>(doc);
            }
            return null;
        });

        when(elasticsearchOperations.delete(anyString(), any(IndexCoordinates.class))).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            docStore.remove(id);
            return id;
        });

        when(elasticsearchOperations.search(any(Query.class), any(Class.class), any(IndexCoordinates.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            Map<String, Object> queryParams = extractQueryParams(query);

            List<Map<String, Object>> allDocs = new ArrayList<>(docStore.values());
            List<Map<String, Object>> filtered = filterDocs(allDocs, queryParams);

            Pageable pageable = query.getPageable();
            long total = filtered.size();

            List<Map<String, Object>> pageContent = new ArrayList<>();
            if (pageable != null && pageable.isPaged()) {
                int from = (int) pageable.getOffset();
                int to = Math.min(from + pageable.getPageSize(), filtered.size());
                if (from < filtered.size()) {
                    pageContent.addAll(filtered.subList(from, to));
                }
            } else {
                pageContent.addAll(filtered);
            }

            List<SearchHit<Map<String, Object>>> searchHits = new ArrayList<>();
            for (Map<String, Object> doc : pageContent) {
                @SuppressWarnings("unchecked")
                SearchHit<Map<String, Object>> hit = mock(SearchHit.class);
                when(hit.getContent()).thenReturn(doc);
                searchHits.add(hit);
            }

            SearchHits<Map<String, Object>> result = mock(SearchHits.class);
            when(result.getSearchHits()).thenReturn(searchHits);
            when(result.getTotalHits()).thenReturn(total);
            return result;
        });

        when(elasticsearchOperations.count(any(Query.class), any(IndexCoordinates.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            Map<String, Object> queryParams = extractQueryParams(query);
            List<Map<String, Object>> allDocs = new ArrayList<>(docStore.values());
            List<Map<String, Object>> filtered = filterDocs(allDocs, queryParams);
            return (long) filtered.size();
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractQueryParams(Query query) {
        Map<String, Object> params = new LinkedHashMap<>();
        try {
            Field criteriaField = null;
            Class<?> clazz = query.getClass();
            while (clazz != null && clazz != Object.class) {
                try {
                    criteriaField = clazz.getDeclaredField("criteria");
                    break;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (criteriaField != null) {
                criteriaField.setAccessible(true);
                Object criteria = criteriaField.get(query);
                if (criteria != null) {
                    extractCriteriaFields(criteria, params);
                }
            }
        } catch (Exception ignored) {
        }
        return params;
    }

    private void extractCriteriaFields(Object criteria, Map<String, Object> params) {
        try {
            String fieldName = null;
            Class<?> clazz = criteria.getClass();
            Field field = null;
            while (clazz != null && clazz != Object.class) {
                try {
                    field = clazz.getDeclaredField("field");
                    break;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (field != null) {
                field.setAccessible(true);
                Object fieldVal = field.get(criteria);
                if (fieldVal != null) {
                    fieldName = fieldVal.toString();
                }
            }

            Object value = getCriteriaValue(criteria);
            if (fieldName != null && value != null && !"_id".equals(fieldName)) {
                params.put(fieldName, value);
            }

            try {
                Field subCriteriaField = null;
                Class<?> c = criteria.getClass();
                while (c != null && c != Object.class) {
                    try {
                        subCriteriaField = c.getDeclaredField("subCriteria");
                        break;
                    } catch (NoSuchFieldException e) {
                        c = c.getSuperclass();
                    }
                }
                if (subCriteriaField != null) {
                    subCriteriaField.setAccessible(true);
                    Object subCriteria = subCriteriaField.get(criteria);
                    if (subCriteria instanceof Iterable) {
                        for (Object sub : (Iterable<?>) subCriteria) {
                            extractCriteriaFields(sub, params);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    private Object getCriteriaValue(Object criteria) {
        try {
            Class<?> clazz = criteria.getClass();
            Field entriesField = null;
            while (clazz != null && clazz != Object.class) {
                try {
                    entriesField = clazz.getDeclaredField("queryCriteriaEntries");
                    break;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (entriesField != null) {
                entriesField.setAccessible(true);
                Object entries = entriesField.get(criteria);
                if (entries instanceof Iterable) {
                    for (Object entry : (Iterable<?>) entries) {
                        Field valueField = null;
                        Class<?> entryClazz = entry.getClass();
                        while (entryClazz != null && entryClazz != Object.class) {
                            try {
                                valueField = entryClazz.getDeclaredField("value");
                                break;
                            } catch (NoSuchFieldException e) {
                                entryClazz = entryClazz.getSuperclass();
                            }
                        }
                        if (valueField != null) {
                            valueField.setAccessible(true);
                            Object value = valueField.get(entry);
                            if (value != null) {
                                return value;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private List<Map<String, Object>> filterDocs(List<Map<String, Object>> docs, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return new ArrayList<>(docs);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            boolean match = true;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                Object docValue = doc.get(entry.getKey());
                Object paramValue = entry.getValue();
                if (docValue == null || !docValue.equals(paramValue)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                result.add(doc);
            }
        }
        return result;
    }

    @Test
    @DisplayName("测试保存文章（新增）")
    void testSave_Insert() {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Test Article Title");
        article.put("content", "This is test article content.");
        article.put("author", "test_author");
        article.put("category", "tech");
        article.put("viewCount", 100L);
        article.put("status", 1);

        Map<String, Object> result = storage.save(article);

        assertNotNull(result);
        assertNotNull(result.get("id"), "ID 应该自动生成");
        assertEquals("Test Article Title", result.get("title"));
        assertEquals("test_author", result.get("author"));
        assertNotNull(result.get("created_at"), "应该自动填充创建时间");
        assertNotNull(result.get("updated_at"), "应该自动填充更新时间");
    }

    @Test
    @DisplayName("测试保存文章（更新）")
    void testSave_Update() {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Original Title");
        article.put("content", "Original content");
        article.put("author", "original_author");
        article.put("category", "news");

        Map<String, Object> saved = storage.save(article);
        String id = (String) saved.get("id");

        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("id", id);
        updateData.put("title", "Updated Title");
        updateData.put("viewCount", 200L);

        Map<String, Object> result = storage.save(updateData);

        assertEquals(id, result.get("id"));
        assertEquals("Updated Title", result.get("title"));
        assertEquals(200L, result.get("viewCount"));
    }

    @Test
    @DisplayName("测试根据ID查询")
    void testFindById() {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Find By Id Test");
        article.put("content", "Content for find by id test");
        article.put("author", "test_author");

        Map<String, Object> saved = storage.save(article);
        String id = (String) saved.get("id");

        Map<String, Object> result = storage.findById(id);

        assertNotNull(result);
        assertEquals(id, result.get("id"));
        assertEquals("Find By Id Test", result.get("title"));
    }

    @Test
    @DisplayName("测试条件查询单条")
    void testQueryOne() {
        Map<String, Object> article1 = new LinkedHashMap<>();
        article1.put("title", "Article One");
        article1.put("content", "Content one");
        article1.put("author", "author_1");
        article1.put("category", "tech");
        storage.save(article1);

        Map<String, Object> article2 = new LinkedHashMap<>();
        article2.put("title", "Article Two");
        article2.put("content", "Content two");
        article2.put("author", "author_2");
        article2.put("category", "news");
        storage.save(article2);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "tech");

        Map<String, Object> result = storage.queryOne(params);

        assertNotNull(result);
        assertEquals("Article One", result.get("title"));
        assertEquals("tech", result.get("category"));
    }

    @Test
    @DisplayName("测试条件查询列表")
    void testQueryList() {
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "Tech Article " + i);
            article.put("content", "Tech content " + i);
            article.put("author", "tech_author");
            article.put("category", i <= 3 ? "tech" : "news");
            storage.save(article);
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "tech");

        List<Map<String, Object>> results = storage.queryList(params);

        assertNotNull(results);
        assertEquals(3, results.size());
    }

    @Test
    @DisplayName("测试分页查询")
    void testQueryPage() {
        for (int i = 1; i <= 25; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "Page Article " + i);
            article.put("content", "Page content " + i);
            article.put("author", "page_author");
            article.put("category", "page_category");
            storage.save(article);
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
    @DisplayName("测试删除文章")
    void testRemoveById() {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Delete Test Article");
        article.put("content", "Content to delete");
        article.put("author", "delete_author");

        Map<String, Object> saved = storage.save(article);
        String id = (String) saved.get("id");

        storage.removeById(id);

        Map<String, Object> result = storage.findById(id);
        assertNull(result);
    }

    @Test
    @DisplayName("测试批量保存")
    void testSaveBatch() {
        List<Map<String, Object>> articles = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "Batch Article " + i);
            article.put("content", "Batch content " + i);
            article.put("author", "batch_author");
            articles.add(article);
        }

        List<Map<String, Object>> results = storage.saveBatch(articles);

        assertNotNull(results);
        assertEquals(5, results.size());
        for (Map<String, Object> r : results) {
            assertNotNull(r.get("id"));
        }
    }

    @Test
    @DisplayName("测试批量删除")
    void testRemoveBatchByIds() {
        List<Object> ids = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "Batch Remove " + i);
            article.put("content", "Content " + i);
            article.put("author", "batch_remove_author");
            Map<String, Object> saved = storage.save(article);
            ids.add(saved.get("id"));
        }

        storage.removeBatchByIds(ids);

        for (Object id : ids) {
            Map<String, Object> result = storage.findById(id);
            assertNull(result);
        }
    }

    @Test
    @DisplayName("测试批量查询")
    void testListByIds() {
        List<Object> ids = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "List By Ids " + i);
            article.put("content", "Content " + i);
            article.put("author", "list_author");
            Map<String, Object> saved = storage.save(article);
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
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "Count Article " + i);
            article.put("content", "Count content " + i);
            article.put("author", "count_author");
            article.put("category", i <= 3 ? "tech" : "news");
            storage.save(article);
        }

        long count = storage.count(null);
        assertEquals(5, count);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "tech");
        long conditionCount = storage.count(params);
        assertEquals(3, conditionCount);
    }

    @Test
    @DisplayName("测试判断存在")
    void testExists() {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Exists Test Article");
        article.put("content", "Content for exists test");
        article.put("author", "exists_author");
        article.put("category", "exists_category");
        storage.save(article);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("author", "exists_author");
        boolean exists = storage.exists(params);
        assertTrue(exists);

        params.put("author", "not_exists_author");
        boolean notExists = storage.exists(params);
        assertFalse(notExists);
    }

    @Test
    @DisplayName("测试自动填充时间字段")
    void testAutoFill() {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "AutoFill Test Article");
        article.put("content", "Content for auto fill test");
        article.put("author", "autofill_author");

        Map<String, Object> result = storage.save(article);

        assertNotNull(result.get("created_at"));
        assertNotNull(result.get("updated_at"));
    }
}
