/*
Copyright 2023 Structure Projects

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	`http://www.apache.org/licenses/LICENSE-2.0`

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package cn.structure.infra.sample.elasticsearch;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.domain.repository.UserRepository;
import cn.structure.infra.sample.elasticsearch.config.ElasticsearchTestConfig;
import cn.structure.infra.sample.elasticsearch.config.MockElasticsearchConfiguration;
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

@SpringBootTest(classes = ElasticsearchTestConfig.class)
@ActiveProfiles("es-test")
@Import(MockElasticsearchConfiguration.class)
@DisplayName("Elasticsearch 仓储详细测试")
class UserElasticsearchDetailRepositoryTest {

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
}
