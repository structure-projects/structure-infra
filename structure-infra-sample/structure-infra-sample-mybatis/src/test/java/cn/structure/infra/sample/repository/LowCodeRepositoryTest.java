package cn.structure.infra.sample.repository;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.lowcode.repository.LowCodeRepository;
import cn.structure.infra.sample.lowcode.LowCodeTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 低代码仓储测试类 - MySQL/H2 实现
 * <p>
 * 测试 LowCodeRepository 的所有 CRUD 方法，
 * 验证低代码仓储路由和 MySQL 存储实现的正确性。
 * <p>
 * 使用 H2 内存数据库进行测试，通过 YAML 配置定义低代码资源。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@SpringBootTest(classes = LowCodeTestConfig.class, properties = {
        "structure.infra.lowcode.enabled=true",
        "structure.infra.lowcode.resources.lc_user.schema.table-name=t_lowcode_user",
        "structure.infra.lowcode.resources.lc_user.schema.fields.id.type=long",
        "structure.infra.lowcode.resources.lc_user.schema.fields.id.primary-key=true",
        "structure.infra.lowcode.resources.lc_user.schema.fields.id.auto-increment=true",
        "structure.infra.lowcode.resources.lc_user.schema.fields.username.type=string",
        "structure.infra.lowcode.resources.lc_user.schema.fields.username.length=64",
        "structure.infra.lowcode.resources.lc_user.schema.fields.username.nullable=false",
        "structure.infra.lowcode.resources.lc_user.schema.fields.username.index=true",
        "structure.infra.lowcode.resources.lc_user.schema.fields.email.type=string",
        "structure.infra.lowcode.resources.lc_user.schema.fields.email.length=128",
        "structure.infra.lowcode.resources.lc_user.schema.fields.email.index=true",
        "structure.infra.lowcode.resources.lc_user.schema.fields.age.type=int",
        "structure.infra.lowcode.resources.lc_user.schema.fields.status.type=string",
        "structure.infra.lowcode.resources.lc_user.schema.fields.status.length=16",
        "structure.infra.lowcode.resources.lc_user.schema.fields.status.default-value=active",
        "structure.infra.lowcode.resources.lc_user.schema.fields.created_at.type=datetime",
        "structure.infra.lowcode.resources.lc_user.schema.fields.created_at.auto-fill=create",
        "structure.infra.lowcode.resources.lc_user.schema.fields.updated_at.type=datetime",
        "structure.infra.lowcode.resources.lc_user.schema.fields.updated_at.auto-fill=create_update",
        "structure.infra.lowcode.resources.lc_user.repository.type=mysql"
})
@DisplayName("低代码仓储 - MySQL 实现测试")
class LowCodeRepositoryTest {

    @Autowired
    private LowCodeRepository lowCodeRepository;

    private static final String RESOURCE_NAME = "lc_user";

    /**
     * 构建用户测试数据
     */
    private Map<String, Object> createUser(String username, String email, Integer age) {
        Map<String, Object> user = new HashMap<>();
        user.put("username", username);
        user.put("email", email);
        user.put("age", age);
        return user;
    }

    @Test
    @DisplayName("测试保存用户 - 新增")
    void testSave_Insert() {
        Map<String, Object> user = createUser("zhangsan", "zhangsan@example.com", 25);
        Map<String, Object> saved = lowCodeRepository.save(RESOURCE_NAME, user);

        assertNotNull(saved);
        assertNotNull(saved.get("id"));
        assertEquals("zhangsan", saved.get("username"));
        assertEquals("zhangsan@example.com", saved.get("email"));
        assertEquals(25, saved.get("age"));
        assertNotNull(saved.get("created_at"));
        assertNotNull(saved.get("updated_at"));

        System.out.println("保存用户成功: " + saved);
    }

    @Test
    @DisplayName("测试保存用户 - 更新")
    void testSave_Update() {
        Map<String, Object> user = createUser("updateUser", "update@test.com", 20);
        Map<String, Object> saved = lowCodeRepository.save(RESOURCE_NAME, user);
        assertNotNull(saved);
        Object id = saved.get("id");

        saved.put("email", "updated@test.com");
        saved.put("age", 30);
        Map<String, Object> updated = lowCodeRepository.save(RESOURCE_NAME, saved);

        assertEquals(id, updated.get("id"));
        assertEquals("updateUser", updated.get("username"));
        assertEquals("updated@test.com", updated.get("email"));
        assertEquals(30, updated.get("age"));

        System.out.println("更新用户成功: " + updated);
    }

    @Test
    @DisplayName("测试根据ID查询 - findById")
    void testFindById() {
        Map<String, Object> user = createUser("lisi", "lisi@example.com", 30);
        Map<String, Object> saved = lowCodeRepository.save(RESOURCE_NAME, user);

        Map<String, Object> found = lowCodeRepository.findById(RESOURCE_NAME, saved.get("id"));
        assertNotNull(found);
        assertEquals(saved.get("id"), found.get("id"));
        assertEquals("lisi", found.get("username"));
    }

    @Test
    @DisplayName("测试根据ID查询 - findById 不存在")
    void testFindById_NotFound() {
        Map<String, Object> found = lowCodeRepository.findById(RESOURCE_NAME, 99999L);
        assertNull(found);
    }

    @Test
    @DisplayName("测试 queryById")
    void testQueryById() {
        Map<String, Object> user = createUser("wangwu", "wangwu@example.com", 28);
        Map<String, Object> saved = lowCodeRepository.save(RESOURCE_NAME, user);

        Map<String, Object> found = lowCodeRepository.queryById(RESOURCE_NAME, saved.get("id"));
        assertNotNull(found);
        assertEquals("wangwu", found.get("username"));
    }

    @Test
    @DisplayName("测试 queryByIdOptional - 存在")
    void testQueryByIdOptional_Exists() {
        Map<String, Object> user = createUser("zhaoliu", "zhaoliu@example.com", 35);
        Map<String, Object> saved = lowCodeRepository.save(RESOURCE_NAME, user);

        Optional<Map<String, Object>> optional = lowCodeRepository.queryByIdOptional(RESOURCE_NAME, saved.get("id"));
        assertTrue(optional.isPresent());
        assertEquals("zhaoliu", optional.get().get("username"));
    }

    @Test
    @DisplayName("测试 queryByIdOptional - 不存在")
    void testQueryByIdOptional_NotExists() {
        Optional<Map<String, Object>> optional = lowCodeRepository.queryByIdOptional(RESOURCE_NAME, 99999L);
        assertFalse(optional.isPresent());
    }

    @Test
    @DisplayName("测试 queryOne - 条件查询单条")
    void testQueryOne() {
        lowCodeRepository.save(RESOURCE_NAME, createUser("qUser1", "q1@test.com", 20));
        lowCodeRepository.save(RESOURCE_NAME, createUser("qUser2", "q2@test.com", 25));

        Map<String, Object> condition = new HashMap<>();
        condition.put("username", "qUser1");

        Map<String, Object> found = lowCodeRepository.queryOne(RESOURCE_NAME, condition);
        assertNotNull(found);
        assertEquals("qUser1", found.get("username"));
        assertEquals("q1@test.com", found.get("email"));
    }

    @Test
    @DisplayName("测试 queryOneOptional")
    void testQueryOneOptional() {
        lowCodeRepository.save(RESOURCE_NAME, createUser("optUser", "opt@test.com", 22));

        Map<String, Object> condition = new HashMap<>();
        condition.put("username", "optUser");

        Optional<Map<String, Object>> optional = lowCodeRepository.queryOneOptional(RESOURCE_NAME, condition);
        assertTrue(optional.isPresent());
        assertEquals("optUser", optional.get().get("username"));
    }

    @Test
    @DisplayName("测试 queryOneOptional - 不存在")
    void testQueryOneOptional_NotExists() {
        Map<String, Object> condition = new HashMap<>();
        condition.put("username", "nonexistent");

        Optional<Map<String, Object>> optional = lowCodeRepository.queryOneOptional(RESOURCE_NAME, condition);
        assertFalse(optional.isPresent());
    }

    @Test
    @DisplayName("测试 queryList - 查询全部")
    void testQueryList_All() {
        long beforeCount = lowCodeRepository.count(RESOURCE_NAME, null);

        lowCodeRepository.save(RESOURCE_NAME, createUser("listUser1", "list1@test.com", 20));
        lowCodeRepository.save(RESOURCE_NAME, createUser("listUser2", "list2@test.com", 25));
        lowCodeRepository.save(RESOURCE_NAME, createUser("listUser3", "list3@test.com", 30));

        List<Map<String, Object>> list = lowCodeRepository.queryList(RESOURCE_NAME, null);
        assertEquals(beforeCount + 3, list.size());
    }

    @Test
    @DisplayName("测试 queryList - 条件查询")
    void testQueryList_ByCondition() {
        lowCodeRepository.save(RESOURCE_NAME, createUser("ageUser1", "age1@test.com", 18));
        lowCodeRepository.save(RESOURCE_NAME, createUser("ageUser2", "age2@test.com", 25));
        lowCodeRepository.save(RESOURCE_NAME, createUser("ageUser3", "age3@test.com", 18));

        Map<String, Object> condition = new HashMap<>();
        condition.put("age", 18);

        List<Map<String, Object>> list = lowCodeRepository.queryList(RESOURCE_NAME, condition);
        assertTrue(list.size() >= 2);
        assertTrue(list.stream().allMatch(u -> Integer.valueOf(18).equals(u.get("age"))));
    }

    @Test
    @DisplayName("测试 queryList - 空列表")
    void testQueryList_Empty() {
        Map<String, Object> condition = new HashMap<>();
        condition.put("age", 999);

        List<Map<String, Object>> list = lowCodeRepository.queryList(RESOURCE_NAME, condition);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    @DisplayName("测试 queryPage - 分页查询")
    void testQueryPage() {
        for (int i = 1; i <= 15; i++) {
            lowCodeRepository.save(RESOURCE_NAME, createUser("pageUser" + i, "page" + i + "@test.com", 20 + i));
        }

        ReqPage reqPage = new ReqPage();
        reqPage.setPage(2);
        reqPage.setSize(5);

        ResPage<Map<String, Object>> page = lowCodeRepository.queryPage(RESOURCE_NAME, reqPage);

        assertNotNull(page);
        assertEquals(2, page.getCurrent());
        assertEquals(5, page.getSize());
        assertTrue(page.getTotal() >= 15);
        assertEquals(5, page.getRecords().size());
        assertTrue(page.getPages() >= 3);

        System.out.println("分页查询结果: 当前页=" + page.getCurrent()
                + ", 总页数=" + page.getPages()
                + ", 每页=" + page.getSize()
                + ", 总数=" + page.getTotal()
                + ", 记录数=" + page.getRecords().size());
    }

    @Test
    @DisplayName("测试删除用户 - removeById")
    void testRemoveById() {
        Map<String, Object> user = createUser("delUser", "del@test.com", 20);
        Map<String, Object> saved = lowCodeRepository.save(RESOURCE_NAME, user);
        assertNotNull(lowCodeRepository.findById(RESOURCE_NAME, saved.get("id")));

        lowCodeRepository.removeById(RESOURCE_NAME, saved.get("id"));
        assertNull(lowCodeRepository.findById(RESOURCE_NAME, saved.get("id")));
    }

    @Test
    @DisplayName("测试 saveBatch - 批量保存")
    void testSaveBatch() {
        List<Map<String, Object>> users = List.of(
                createUser("batchUser1", "batch1@test.com", 21),
                createUser("batchUser2", "batch2@test.com", 22),
                createUser("batchUser3", "batch3@test.com", 23)
        );

        List<Map<String, Object>> savedList = lowCodeRepository.saveBatch(RESOURCE_NAME, users);

        assertEquals(3, savedList.size());
        for (Map<String, Object> saved : savedList) {
            assertNotNull(saved.get("id"));
            assertNotNull(saved.get("created_at"));
        }
    }

    @Test
    @DisplayName("测试 removeBatchByIds - 批量删除")
    void testRemoveBatchByIds() {
        List<Map<String, Object>> users = List.of(
                createUser("batchDel1", "bd1@test.com", 21),
                createUser("batchDel2", "bd2@test.com", 22),
                createUser("batchDel3", "bd3@test.com", 23)
        );
        List<Map<String, Object>> savedList = lowCodeRepository.saveBatch(RESOURCE_NAME, users);
        List<Object> ids = savedList.stream().map(u -> u.get("id")).toList();

        assertEquals(3, lowCodeRepository.listByIds(RESOURCE_NAME, ids).size());

        lowCodeRepository.removeBatchByIds(RESOURCE_NAME, ids);

        assertEquals(0, lowCodeRepository.listByIds(RESOURCE_NAME, ids).size());
    }

    @Test
    @DisplayName("测试 listByIds - 批量查询")
    void testListByIds() {
        List<Map<String, Object>> users = List.of(
                createUser("listById1", "lid1@test.com", 21),
                createUser("listById2", "lid2@test.com", 22),
                createUser("listById3", "lid3@test.com", 23)
        );
        List<Map<String, Object>> savedList = lowCodeRepository.saveBatch(RESOURCE_NAME, users);
        List<Object> ids = savedList.stream().map(u -> u.get("id")).toList();

        List<Map<String, Object>> result = lowCodeRepository.listByIds(RESOURCE_NAME, ids);
        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("测试 count - 统计数量")
    void testCount() {
        long beforeCount = lowCodeRepository.count(RESOURCE_NAME, null);

        lowCodeRepository.save(RESOURCE_NAME, createUser("countUser", "count@test.com", 25));

        long afterCount = lowCodeRepository.count(RESOURCE_NAME, null);
        assertEquals(beforeCount + 1, afterCount);

        Map<String, Object> condition = new HashMap<>();
        condition.put("username", "countUser");
        long countByCondition = lowCodeRepository.count(RESOURCE_NAME, condition);
        assertEquals(1, countByCondition);
    }

    @Test
    @DisplayName("测试 exists - 判断存在")
    void testExists() {
        lowCodeRepository.save(RESOURCE_NAME, createUser("existUser", "exist@test.com", 25));

        Map<String, Object> condition = new HashMap<>();
        condition.put("username", "existUser");
        assertTrue(lowCodeRepository.exists(RESOURCE_NAME, condition));

        Map<String, Object> notExistCondition = new HashMap<>();
        notExistCondition.put("username", "notExistUser");
        assertFalse(lowCodeRepository.exists(RESOURCE_NAME, notExistCondition));
    }

    @Test
    @DisplayName("测试自动填充 - 创建时间和更新时间")
    void testAutoFill() {
        Map<String, Object> user = createUser("autoFillUser", "autofill@test.com", 25);
        Map<String, Object> saved = lowCodeRepository.save(RESOURCE_NAME, user);

        assertNotNull(saved.get("created_at"), "创建时间应该被自动填充");
        assertNotNull(saved.get("updated_at"), "更新时间应该被自动填充");
        Object createdAt = saved.get("created_at");
        Object updatedAt = saved.get("updated_at");
        assertNotNull(createdAt);
        assertNotNull(updatedAt);
        assertEquals(String.valueOf(createdAt).substring(0, 19), String.valueOf(updatedAt).substring(0, 19),
                "刚创建时创建时间和更新时间应该在秒级相同");
    }

    @Test
    @DisplayName("测试默认值字段")
    void testDefaultValue() {
        Map<String, Object> user = createUser("defaultUser", "default@test.com", 25);
        Map<String, Object> saved = lowCodeRepository.save(RESOURCE_NAME, user);

        assertEquals("active", saved.get("status"), "status 应该有默认值 active");
    }
}
