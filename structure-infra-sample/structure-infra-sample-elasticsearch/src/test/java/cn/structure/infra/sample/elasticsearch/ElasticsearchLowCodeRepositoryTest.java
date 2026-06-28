package cn.structure.infra.sample.elasticsearch;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.lowcode.model.AutoFillType;
import cn.structure.infra.lowcode.model.FieldSchema;
import cn.structure.infra.lowcode.model.FieldType;
import cn.structure.infra.lowcode.model.ResourceSchema;
import cn.structure.infra.lowcode.router.LowCodeRepositoryRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Elasticsearch 低代码仓储测试
 * <p>
 * 测试 Elasticsearch 低代码存储的各种功能：
 * <ul>
 *   <li>CRUD 操作</li>
 *   <li>批量操作</li>
 *   <li>分页查询</li>
 *   <li>条件查询</li>
 *   <li>自动填充</li>
 * </ul>
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@SpringBootTest(classes = cn.structure.infra.sample.elasticsearch.config.ElasticsearchLowCodeTestConfig.class)
@ActiveProfiles("es-test")
@DisplayName("Elasticsearch 低代码仓储测试")
class ElasticsearchLowCodeRepositoryTest {

    @Autowired(required = false)
    private LowCodeRepositoryRouter lowCodeRouter;

    @Autowired(required = false)
    private ElasticsearchOperations elasticsearchOperations;

    private static final String RESOURCE_NAME = "article";
    private static final String INDEX_NAME = "t_lowcode_article";

    @BeforeEach
    void setUp() {
        if (lowCodeRouter == null || elasticsearchOperations == null) {
            return;
        }

        // 清理测试索引数据
        try {
            if (elasticsearchOperations.indexOps(IndexCoordinates.of(INDEX_NAME)).exists()) {
                elasticsearchOperations.delete(IndexCoordinates.of(INDEX_NAME));
            }
        } catch (Exception e) {
            // 索引不存在，忽略
        }

        // 注册文章资源
        ResourceSchema schema = ResourceSchema.builder()
                .resourceName(RESOURCE_NAME)
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

        lowCodeRouter.registerResource(RESOURCE_NAME, schema, null);
    }

    @Test
    @DisplayName("测试保存文章（新增）")
    void testSave_Insert() {
        if (lowCodeRouter == null) {
            return;
        }

        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Test Article Title");
        article.put("content", "This is test article content.");
        article.put("author", "test_author");
        article.put("category", "tech");
        article.put("viewCount", 100L);
        article.put("status", 1);

        Map<String, Object> result = lowCodeRouter.save(RESOURCE_NAME, article);

        assertNotNull(result);
        assertNotNull(result.get("id"), "ID 应该自动生成");
        assertEquals("Test Article Title", result.get("title"));
        assertEquals("test_author", result.get("author"));
        assertNotNull(result.get("created_at"), "应该自动填充创建时间");
    }

    @Test
    @DisplayName("测试保存文章（更新）")
    void testSave_Update() {
        if (lowCodeRouter == null) {
            return;
        }

        // 先插入
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Original Title");
        article.put("content", "Original content");
        article.put("author", "original_author");
        article.put("category", "news");

        Map<String, Object> saved = lowCodeRouter.save(RESOURCE_NAME, article);
        String id = (String) saved.get("id");

        // 再更新
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("id", id);
        updateData.put("title", "Updated Title");
        updateData.put("viewCount", 200L);

        Map<String, Object> result = lowCodeRouter.save(RESOURCE_NAME, updateData);

        assertEquals(id, result.get("id"));
        assertEquals("Updated Title", result.get("title"));
        assertEquals("original_author", result.get("author"));
    }

    @Test
    @DisplayName("测试根据ID查询")
    void testFindById() {
        if (lowCodeRouter == null) {
            return;
        }

        // 先插入一条数据
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Find By Id Test");
        article.put("content", "Content for find by id test");
        article.put("author", "test_author");

        Map<String, Object> saved = lowCodeRouter.save(RESOURCE_NAME, article);
        String id = (String) saved.get("id");

        // 查询
        Map<String, Object> result = lowCodeRouter.findById(RESOURCE_NAME, id);

        assertNotNull(result);
        assertEquals(id, result.get("id"));
        assertEquals("Find By Id Test", result.get("title"));
    }

    @Test
    @DisplayName("测试条件查询单条")
    void testQueryOne() {
        if (lowCodeRouter == null) {
            return;
        }

        // 插入测试数据
        Map<String, Object> article1 = new LinkedHashMap<>();
        article1.put("title", "Article One");
        article1.put("content", "Content one");
        article1.put("author", "author_1");
        article1.put("category", "tech");
        lowCodeRouter.save(RESOURCE_NAME, article1);

        Map<String, Object> article2 = new LinkedHashMap<>();
        article2.put("title", "Article Two");
        article2.put("content", "Content two");
        article2.put("author", "author_2");
        article2.put("category", "news");
        lowCodeRouter.save(RESOURCE_NAME, article2);

        // 条件查询
        Map<String, Object> params = new HashMap<>();
        params.put("category", "tech");

        Map<String, Object> result = lowCodeRouter.queryOne(RESOURCE_NAME, params);

        assertNotNull(result);
        assertEquals("Article One", result.get("title"));
        assertEquals("tech", result.get("category"));
    }

    @Test
    @DisplayName("测试条件查询列表")
    void testQueryList() {
        if (lowCodeRouter == null) {
            return;
        }

        // 插入测试数据
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "Tech Article " + i);
            article.put("content", "Tech content " + i);
            article.put("author", "tech_author");
            article.put("category", "tech");
            lowCodeRouter.save(RESOURCE_NAME, article);
        }

        // 条件查询
        Map<String, Object> params = new HashMap<>();
        params.put("category", "tech");

        List<Map<String, Object>> results = lowCodeRouter.queryList(RESOURCE_NAME, params);

        assertNotNull(results);
        assertTrue(results.size() >= 3);
    }

    @Test
    @DisplayName("测试分页查询")
    void testQueryPage() {
        if (lowCodeRouter == null) {
            return;
        }

        // 插入测试数据
        for (int i = 1; i <= 15; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "Page Article " + i);
            article.put("content", "Page content " + i);
            article.put("author", "page_author");
            article.put("category", "page_category");
            lowCodeRouter.save(RESOURCE_NAME, article);
        }

        // 分页查询
        ReqPage reqPage = new ReqPage();
        reqPage.setPage(1);
        reqPage.setSize(10);

        ResPage<Map<String, Object>> page = lowCodeRouter.queryPage(RESOURCE_NAME, reqPage);

        assertNotNull(page);
        assertEquals(1L, page.getCurrent());
        assertEquals(10L, page.getSize());
        assertTrue(page.getTotal() >= 15);
    }

    @Test
    @DisplayName("测试删除文章")
    void testRemoveById() {
        if (lowCodeRouter == null) {
            return;
        }

        // 先插入一条数据
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Delete Test Article");
        article.put("content", "Content to delete");
        article.put("author", "delete_author");

        Map<String, Object> saved = lowCodeRouter.save(RESOURCE_NAME, article);
        String id = (String) saved.get("id");

        // 删除
        lowCodeRouter.removeById(RESOURCE_NAME, id);

        // 验证删除
        Map<String, Object> result = lowCodeRouter.findById(RESOURCE_NAME, id);
        assertNull(result);
    }

    @Test
    @DisplayName("测试批量保存")
    void testSaveBatch() {
        if (lowCodeRouter == null) {
            return;
        }

        List<Map<String, Object>> articles = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "Batch Article " + i);
            article.put("content", "Batch content " + i);
            article.put("author", "batch_author");
            articles.add(article);
        }

        List<Map<String, Object>> results = lowCodeRouter.saveBatch(RESOURCE_NAME, articles);

        assertNotNull(results);
        assertEquals(5, results.size());
        results.forEach(r -> assertNotNull(r.get("id")));
    }

    @Test
    @DisplayName("测试批量删除")
    void testRemoveBatchByIds() {
        if (lowCodeRouter == null) {
            return;
        }

        // 先插入几条数据
        List<Object> ids = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "Batch Remove " + i);
            article.put("content", "Content " + i);
            article.put("author", "batch_remove_author");
            Map<String, Object> saved = lowCodeRouter.save(RESOURCE_NAME, article);
            ids.add(saved.get("id"));
        }

        // 批量删除
        lowCodeRouter.removeBatchByIds(RESOURCE_NAME, ids);

        // 验证删除
        ids.forEach(id -> {
            Map<String, Object> result = lowCodeRouter.findById(RESOURCE_NAME, id);
            assertNull(result);
        });
    }

    @Test
    @DisplayName("测试批量查询")
    void testListByIds() {
        if (lowCodeRouter == null) {
            return;
        }

        // 先插入几条数据
        List<Object> ids = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "List By Ids " + i);
            article.put("content", "Content " + i);
            article.put("author", "list_author");
            Map<String, Object> saved = lowCodeRouter.save(RESOURCE_NAME, article);
            ids.add(saved.get("id"));
        }

        // 批量查询
        List<Map<String, Object>> results = lowCodeRouter.listByIds(RESOURCE_NAME, ids);

        assertNotNull(results);
        assertTrue(results.size() >= 3);
    }

    @Test
    @DisplayName("测试统计数量")
    void testCount() {
        if (lowCodeRouter == null) {
            return;
        }

        // 插入测试数据
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", "Count Article " + i);
            article.put("content", "Count content " + i);
            article.put("author", "count_author");
            article.put("category", "count_category");
            lowCodeRouter.save(RESOURCE_NAME, article);
        }

        // 统计
        long count = lowCodeRouter.count(RESOURCE_NAME, null);
        assertTrue(count >= 5);

        // 条件统计
        Map<String, Object> params = new HashMap<>();
        params.put("category", "count_category");
        long conditionCount = lowCodeRouter.count(RESOURCE_NAME, params);
        assertTrue(conditionCount >= 5);
    }

    @Test
    @DisplayName("测试判断存在")
    void testExists() {
        if (lowCodeRouter == null) {
            return;
        }

        // 插入测试数据
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Exists Test Article");
        article.put("content", "Content for exists test");
        article.put("author", "exists_author");
        article.put("category", "exists_category");
        lowCodeRouter.save(RESOURCE_NAME, article);

        // 判断存在
        Map<String, Object> params = new HashMap<>();
        params.put("author", "exists_author");

        boolean exists = lowCodeRouter.exists(RESOURCE_NAME, params);
        assertTrue(exists);

        // 判断不存在
        params.put("author", "not_exists_author");
        boolean notExists = lowCodeRouter.exists(RESOURCE_NAME, params);
        assertFalse(notExists);
    }

    @Test
    @DisplayName("测试自动填充时间字段")
    void testAutoFill() {
        if (lowCodeRouter == null) {
            return;
        }

        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "AutoFill Test Article");
        article.put("content", "Content for auto fill test");
        article.put("author", "autofill_author");

        Map<String, Object> result = lowCodeRouter.save(RESOURCE_NAME, article);

        assertNotNull(result.get("created_at"));
        assertNotNull(result.get("updated_at"));
    }
}