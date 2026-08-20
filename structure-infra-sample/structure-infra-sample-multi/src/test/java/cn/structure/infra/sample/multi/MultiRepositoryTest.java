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

package cn.structure.infra.sample.multi;

import cn.structure.infra.sample.multi.config.MockMongoConfiguration;
import cn.structure.infra.sample.multi.domain.entity.UserEntity;
import cn.structure.infra.sample.multi.service.MultiRepositoryService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest(classes = cn.structure.infra.sample.multi.config.MultiTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class MultiRepositoryTest {

    @Autowired
    private MultiRepositoryService multiRepositoryService;

    @Autowired
    private MockMongoConfiguration mockMongoConfig;

    private UserEntity testUser;

    @BeforeEach
    public void setUp() {
        mockMongoConfig.reset();
        testUser = new UserEntity();
        testUser.setUsername("test_multi_user");
        testUser.setEmail("test_multi@example.com");
        testUser.setAge(25);
    }

    @Test
    public void testSaveToMybatisAndFind() {
        log.info("=== Test: Save to MyBatis Plus and find ===");
        
        UserEntity saved = multiRepositoryService.saveToMybatis(testUser);
        assertNotNull(saved.getId());
        assertEquals("test_multi_user", saved.getUsername());
        
        UserEntity found = multiRepositoryService.findFromMybatis(saved.getId());
        assertNotNull(found);
        assertEquals("test_multi_user", found.getUsername());
        assertEquals("test_multi@example.com", found.getEmail());
        assertEquals(25, found.getAge());
        
        log.info("✓ MyBatis Plus save and find test passed");
    }

    @Test
    public void testSaveToMongoAndFind() {
        log.info("=== Test: Save to MongoDB and find ===");
        
        UserEntity saved = multiRepositoryService.saveToMongo(testUser);
        assertNotNull(saved.getId());
        assertEquals("test_multi_user", saved.getUsername());
        
        UserEntity found = multiRepositoryService.findFromMongo(saved.getId());
        assertNotNull(found);
        assertEquals("test_multi_user", found.getUsername());
        
        log.info("✓ MongoDB save and find test passed");
    }

    @Test
    public void testDataIsolationBetweenRepositories() {
        log.info("=== Test: Data isolation between repositories ===");
        
        UserEntity mybatisUser = new UserEntity();
        mybatisUser.setUsername("mybatis_only_user");
        mybatisUser.setEmail("mybatis@example.com");
        UserEntity savedToMybatis = multiRepositoryService.saveToMybatis(mybatisUser);
        assertNotNull(savedToMybatis.getId());
        log.info("Saved to MyBatis with id: {}, username: {}", savedToMybatis.getId(), savedToMybatis.getUsername());
        
        UserEntity foundInMongoByName = multiRepositoryService.findByNameFromMongo("mybatis_only_user");
        assertNull(foundInMongoByName, "Data saved to MyBatis should NOT be visible in MongoDB");
        
        UserEntity mongoUser = new UserEntity();
        mongoUser.setUsername("mongo_only_user");
        mongoUser.setEmail("mongo@example.com");
        UserEntity savedToMongo = multiRepositoryService.saveToMongo(mongoUser);
        assertNotNull(savedToMongo.getId());
        log.info("Saved to MongoDB with id: {}, username: {}", savedToMongo.getId(), savedToMongo.getUsername());
        
        UserEntity foundInMybatisByName = multiRepositoryService.findByNameFromMybatis("mongo_only_user");
        assertNull(foundInMybatisByName, "Data saved to MongoDB should NOT be visible in MyBatis");
        
        log.info("✓ Data isolation test passed");
    }

    @Test
    public void testSaveToDefaultRepository() {
        log.info("=== Test: Save to default repository (configured as MYBATIS_PLUS) ===");
        
        UserEntity saved = multiRepositoryService.saveToDefault(testUser);
        assertNotNull(saved.getId());
        
        UserEntity foundInMybatis = multiRepositoryService.findFromMybatis(saved.getId());
        assertNotNull(foundInMybatis);
        assertEquals("test_multi_user", foundInMybatis.getUsername());
        
        log.info("✓ Default repository test passed");
    }

    @Test
    public void testCountAndExistsOperations() {
        log.info("=== Test: Count and exists operations ===");
        
        multiRepositoryService.saveToMybatis(testUser);
        
        long countInMybatis = multiRepositoryService.countInMybatis(new UserEntity());
        assertTrue(countInMybatis >= 1);
        
        UserEntity condition = new UserEntity();
        condition.setUsername("test_multi_user");
        boolean existsInMybatis = multiRepositoryService.existsInMybatis(condition);
        assertTrue(existsInMybatis);
        
        long countInMongo = multiRepositoryService.countInMongo(new UserEntity());
        assertEquals(0, countInMongo);
        
        UserEntity mongoCondition = new UserEntity();
        mongoCondition.setUsername("test_multi_user");
        boolean existsInMongo = multiRepositoryService.existsInMongo(mongoCondition);
        assertFalse(existsInMongo);
        
        log.info("✓ Count and exists operations test passed");
    }

    @Test
    public void testDeleteOperations() {
        log.info("=== Test: Delete operations ===");
        
        UserEntity savedToMybatis = multiRepositoryService.saveToMybatis(testUser);
        assertNotNull(savedToMybatis.getId());
        
        multiRepositoryService.deleteFromMybatis(savedToMybatis.getId());
        
        UserEntity found = multiRepositoryService.findFromMybatis(savedToMybatis.getId());
        assertNull(found);
        
        log.info("✓ Delete operations test passed");
    }

    @Test
    public void testFindByNameOperation() {
        log.info("=== Test: Find by name operation ===");
        
        UserEntity savedToMybatis = multiRepositoryService.saveToMybatis(testUser);
        log.info("Saved to MyBatis with id: {}", savedToMybatis.getId());
        
        UserEntity foundByName = multiRepositoryService.findByNameFromMybatis("test_multi_user");
        assertNotNull(foundByName);
        log.info("Found by name with id: {}", foundByName.getId());
        assertEquals(savedToMybatis.getId(), foundByName.getId());
        
        log.info("✓ Find by name operation test passed");
    }

    @Test
    public void testQueryListOperation() {
        log.info("=== Test: Query list operation ===");
        
        UserEntity user1 = new UserEntity();
        user1.setUsername("user1");
        user1.setEmail("user1@example.com");
        UserEntity user2 = new UserEntity();
        user2.setUsername("user2");
        user2.setEmail("user2@example.com");
        
        multiRepositoryService.saveToMybatis(user1);
        multiRepositoryService.saveToMybatis(user2);
        
        var users = multiRepositoryService.queryListFromMybatis(new UserEntity());
        assertNotNull(users);
        assertTrue(users.size() >= 2);
        
        log.info("✓ Query list operation test passed");
    }

    @Test
    public void testMultiRepositorySwitchingInSequence() {
        log.info("=== Test: Multi repository switching in sequence ===");
        
        UserEntity mybatisUser = new UserEntity();
        mybatisUser.setUsername("mybatis_sequence_user");
        mybatisUser.setEmail("mybatis_sequence@example.com");
        UserEntity mongoUser = new UserEntity();
        mongoUser.setUsername("mongo_sequence_user");
        mongoUser.setEmail("mongo_sequence@example.com");
        
        UserEntity savedToMybatis = multiRepositoryService.saveToMybatis(mybatisUser);
        UserEntity savedToMongo = multiRepositoryService.saveToMongo(mongoUser);
        
        assertNotNull(savedToMybatis.getId());
        assertNotNull(savedToMongo.getId());
        
        UserEntity foundMybatis = multiRepositoryService.findFromMybatis(savedToMybatis.getId());
        UserEntity foundMongo = multiRepositoryService.findFromMongo(savedToMongo.getId());
        
        assertNotNull(foundMybatis);
        assertNotNull(foundMongo);
        assertEquals("mybatis_sequence_user", foundMybatis.getUsername());
        assertEquals("mongo_sequence_user", foundMongo.getUsername());
        
        assertNull(multiRepositoryService.findByNameFromMongo("mybatis_sequence_user"));
        assertNull(multiRepositoryService.findByNameFromMybatis("mongo_sequence_user"));
        
        log.info("✓ Multi repository switching in sequence test passed");
    }
}