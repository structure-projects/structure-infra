package cn.structure.infra.sample.mongodb;

import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.domain.repository.UserRepository;
import cn.structure.infra.sample.mongodb.config.MongoTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MongoDB 仓储测试
 * <p>
 * 测试 MongoDB 实现的用户仓储功能
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@SpringBootTest
@ActiveProfiles("mongo-test")
@Import(MongoTestConfig.class)
@DisplayName("MongoDB 仓储测试")
public class UserMongoRepositoryTest {

    @Autowired(required = false)
    private UserRepository userRepository;

    @Test
    @DisplayName("测试 MongoDB 仓储注入")
    void testMongoRepositoryInjection() {
        assertNotNull(userRepository, "MongoDB 仓储应该被成功注入");
        System.out.println("✓ MongoDB 仓储注入成功: " + userRepository.getClass().getName());
    }

    @Test
    @DisplayName("测试 MongoDB 保存功能（待实现）")
    void testMongoSave() {
        assertNotNull(userRepository, "MongoDB 仓储应该被注入");

        UserEntity user = new UserEntity();
        user.setUsername("mongoTestUser");
        user.setPassword("password123");
        user.setEmail("mongo@test.com");
        user.setAge(25);

        // TODO: 等待 MongoDB 实现完成
        System.out.println("⚠ MongoDB 保存功能待实现，当前返回 null");
    }

    @Test
    @DisplayName("测试 MongoDB 查询功能（待实现）")
    void testMongoFindByName() {
        assertNotNull(userRepository, "MongoDB 仓储应该被注入");

        // TODO: 等待 MongoDB 实现完成
        System.out.println("⚠ MongoDB 查询功能待实现");
    }
}
