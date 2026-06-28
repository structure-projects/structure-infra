package cn.structure.infra.sample.mongodb;

import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.domain.repository.UserRepository;
import cn.structure.infra.sample.mongodb.config.MongoTestConfig;
import cn.structure.infra.sample.mongodb.config.MockMongoConfiguration;
import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MongoTestConfig.class)
@ActiveProfiles("mongo-test")
@Import(MockMongoConfiguration.class)
@DisplayName("MongoDB 仓储测试")
class UserMongoRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private UserEntity createUser(String username, String email, Integer age) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setAge(age);
        user.setPassword("123456");
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return user;
    }

    @Test
    @DisplayName("测试保存用户")
    void testSave() {
        UserEntity user = createUser("zhangsan", "zhangsan@example.com", 25);
        UserEntity saved = userRepository.save(user);

        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("zhangsan", saved.getUsername());
        assertEquals("zhangsan@example.com", saved.getEmail());
        assertEquals(25, saved.getAge());

        System.out.println("保存用户成功: " + saved);
    }

    @Test
    @DisplayName("测试根据ID查询")
    void testFindById() {
        UserEntity user = createUser("lisi", "lisi@example.com", 30);
        UserEntity saved = userRepository.save(user);

        UserEntity found = userRepository.findById(saved.getId());
        assertNotNull(found);
        assertEquals(saved.getId(), found.getId());
        assertEquals("lisi", found.getUsername());
    }

    @Test
    @DisplayName("测试 queryById")
    void testQueryById() {
        UserEntity user = createUser("wangwu", "wangwu@example.com", 28);
        UserEntity saved = userRepository.save(user);

        UserEntity found = userRepository.queryById(saved.getId());
        assertNotNull(found);
        assertEquals("wangwu", found.getUsername());
    }

    @Test
    @DisplayName("测试 queryByIdOptional - 存在")
    void testQueryByIdOptional_Exists() {
        UserEntity user = createUser("zhaoliu", "zhaoliu@example.com", 35);
        UserEntity saved = userRepository.save(user);

        Optional<UserEntity> optional = userRepository.queryByIdOptional(saved.getId());
        assertTrue(optional.isPresent());
        assertEquals("zhaoliu", optional.get().getUsername());
    }

    @Test
    @DisplayName("测试 queryByIdOptional - 不存在")
    void testQueryByIdOptional_NotExists() {
        Optional<UserEntity> optional = userRepository.queryByIdOptional(9999L);
        assertFalse(optional.isPresent());
    }

    @Test
    @DisplayName("测试 queryOne - 条件查询单条")
    void testQueryOne() {
        userRepository.save(createUser("user1", "user1@test.com", 20));
        userRepository.save(createUser("user2", "user2@test.com", 25));

        UserEntity condition = new UserEntity();
        condition.setUsername("user1");

        UserEntity found = userRepository.queryOne(condition);
        assertNotNull(found);
        assertEquals("user1", found.getUsername());
        assertEquals("user1@test.com", found.getEmail());
    }

    @Test
    @DisplayName("测试 queryOneOptional")
    void testQueryOneOptional() {
        userRepository.save(createUser("optUser", "opt@test.com", 22));

        UserEntity condition = new UserEntity();
        condition.setUsername("optUser");

        Optional<UserEntity> optional = userRepository.queryOneOptional(condition);
        assertTrue(optional.isPresent());
        assertEquals("optUser", optional.get().getUsername());
    }

    @Test
    @DisplayName("测试 queryOneOptional - 不存在")
    void testQueryOneOptional_NotExists() {
        UserEntity condition = new UserEntity();
        condition.setUsername("nonexistent");

        Optional<UserEntity> optional = userRepository.queryOneOptional(condition);
        assertFalse(optional.isPresent());
    }

    @Test
    @DisplayName("测试 queryList - 查询全部")
    void testQueryList_All() {
        int beforeCount = userRepository.queryList(null).size();

        userRepository.save(createUser("listUser1", "list1@test.com", 20));
        userRepository.save(createUser("listUser2", "list2@test.com", 25));
        userRepository.save(createUser("listUser3", "list3@test.com", 30));

        List<UserEntity> list = userRepository.queryList(null);
        assertEquals(beforeCount + 3, list.size());
    }

    @Test
    @DisplayName("测试 queryList - 条件查询")
    void testQueryList_ByCondition() {
        userRepository.save(createUser("ageUser1", "age1@test.com", 18));
        userRepository.save(createUser("ageUser2", "age2@test.com", 25));
        userRepository.save(createUser("ageUser3", "age3@test.com", 18));

        UserEntity condition = new UserEntity();
        condition.setAge(18);

        List<UserEntity> list = userRepository.queryList(condition);
        assertTrue(list.size() >= 2);
        assertTrue(list.stream().allMatch(u -> u.getAge() == 18));
    }

    @Test
    @DisplayName("测试 queryList - 列表返回空集合")
    void testQueryList_Empty() {
        UserEntity condition = new UserEntity();
        condition.setAge(999);

        List<UserEntity> list = userRepository.queryList(condition);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    @DisplayName("测试 queryPage - 分页查询")
    void testQueryPage() {
        for (int i = 1; i <= 15; i++) {
            userRepository.save(createUser("pageUser" + i, "page" + i + "@test.com", 20 + i));
        }

        ReqPage reqPage = new ReqPage();
        reqPage.setPage(2);
        reqPage.setSize(5);

        ResPage<UserEntity> page = userRepository.queryPage(reqPage);

        assertNotNull(page);
        assertEquals(2, page.getCurrent());
        assertEquals(5, page.getSize());
        assertTrue(page.getTotal() >= 15);
        assertEquals(5, page.getRecords().size());

        System.out.println("分页查询结果: 当前页=" + page.getCurrent()
                + ", 总页数=" + page.getPages()
                + ", 每页=" + page.getSize()
                + ", 总数=" + page.getTotal()
                + ", 记录数=" + page.getRecords().size());
    }

    @Test
    @DisplayName("测试删除用户")
    void testRemoveById() {
        UserEntity user = createUser("delUser", "del@test.com", 20);
        UserEntity saved = userRepository.save(user);
        assertNotNull(userRepository.findById(saved.getId()));

        userRepository.removeById(saved.getId());
        assertNull(userRepository.findById(saved.getId()));
    }

    @Test
    @DisplayName("测试 Entity <-> PO 转换")
    void testEntityPoConversion() {
        UserEntity user = createUser("convertUser", "convert@test.com", 28);
        UserEntity saved = userRepository.save(user);

        UserEntity found = userRepository.findById(saved.getId());
        assertNotNull(found);
        assertEquals(saved.getId(), found.getId());
        assertEquals(saved.getUsername(), found.getUsername());
        assertEquals(saved.getEmail(), found.getEmail());
        assertEquals(saved.getAge(), found.getAge());
    }

    @Test
    @DisplayName("测试批量保存")
    void testSaveBatch() {
        UserEntity user1 = createUser("batch1", "batch1@test.com", 20);
        UserEntity user2 = createUser("batch2", "batch2@test.com", 25);
        UserEntity user3 = createUser("batch3", "batch3@test.com", 30);

        List<UserEntity> savedList = userRepository.saveBatch(List.of(user1, user2, user3));
        assertNotNull(savedList);
        assertEquals(3, savedList.size());
        savedList.forEach(u -> assertNotNull(u.getId()));
    }

    @Test
    @DisplayName("测试批量删除")
    void testRemoveBatchByIds() {
        UserEntity user1 = userRepository.save(createUser("batchDel1", "bd1@test.com", 20));
        UserEntity user2 = userRepository.save(createUser("batchDel2", "bd2@test.com", 25));

        assertNotNull(userRepository.findById(user1.getId()));
        assertNotNull(userRepository.findById(user2.getId()));

        userRepository.removeBatchByIds(List.of(user1.getId(), user2.getId()));

        assertNull(userRepository.findById(user1.getId()));
        assertNull(userRepository.findById(user2.getId()));
    }

    @Test
    @DisplayName("测试根据ID列表查询")
    void testListByIds() {
        UserEntity user1 = userRepository.save(createUser("listId1", "lid1@test.com", 20));
        UserEntity user2 = userRepository.save(createUser("listId2", "lid2@test.com", 25));
        UserEntity user3 = userRepository.save(createUser("listId3", "lid3@test.com", 30));

        List<UserEntity> list = userRepository.listByIds(List.of(user1.getId(), user3.getId()));
        assertNotNull(list);
        assertEquals(2, list.size());
    }

    @Test
    @DisplayName("测试 count - 全部数量")
    void testCount_All() {
        long beforeCount = userRepository.count(null);

        userRepository.save(createUser("count1", "count1@test.com", 20));
        userRepository.save(createUser("count2", "count2@test.com", 25));

        long afterCount = userRepository.count(null);
        assertEquals(beforeCount + 2, afterCount);
    }

    @Test
    @DisplayName("测试 count - 条件数量")
    void testCount_ByCondition() {
        userRepository.save(createUser("countAge1", "ca1@test.com", 22));
        userRepository.save(createUser("countAge2", "ca2@test.com", 22));
        userRepository.save(createUser("countAge3", "ca3@test.com", 33));

        UserEntity condition = new UserEntity();
        condition.setAge(22);

        long count = userRepository.count(condition);
        assertTrue(count >= 2);
    }

    @Test
    @DisplayName("测试 exists - 存在")
    void testExists_True() {
        UserEntity user = userRepository.save(createUser("existsUser", "exists@test.com", 20));

        UserEntity condition = new UserEntity();
        condition.setUsername("existsUser");

        assertTrue(userRepository.exists(condition));
    }

    @Test
    @DisplayName("测试 exists - 不存在")
    void testExists_False() {
        UserEntity condition = new UserEntity();
        condition.setUsername("nonexistent_user");

        assertFalse(userRepository.exists(condition));
    }
}
