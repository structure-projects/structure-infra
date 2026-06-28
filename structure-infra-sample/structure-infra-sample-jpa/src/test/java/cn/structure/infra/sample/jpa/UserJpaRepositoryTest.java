package cn.structure.infra.sample.jpa;

import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.domain.repository.UserRepository;
import cn.structure.infra.sample.jpa.config.JpaTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JPA 仓储测试
 * <p>
 * 测试 JPA 实现的用户仓储功能
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@SpringBootTest
@ActiveProfiles("jpa-test")
@Import(JpaTestConfig.class)
@DisplayName("JPA 仓储测试")
public class UserJpaRepositoryTest {

    @Autowired(required = false)
    private UserRepository userRepository;

    @Test
    @DisplayName("测试 JPA 仓储注入")
    void testJpaRepositoryInjection() {
        assertNotNull(userRepository, "JPA 仓储应该被成功注入");
        System.out.println("✓ JPA 仓储注入成功: " + userRepository.getClass().getName());
    }

    @Test
    @DisplayName("测试 JPA 保存功能（待实现）")
    void testJpaSave() {
        assertNotNull(userRepository, "JPA 仓储应该被注入");

        UserEntity user = new UserEntity();
        user.setUsername("jpaTestUser");
        user.setPassword("password123");
        user.setEmail("jpa@test.com");
        user.setAge(25);

        // TODO: 等待 JPA 实现完成
        System.out.println("⚠ JPA 保存功能待实现，当前返回 null");
    }

    @Test
    @DisplayName("测试 JPA 查询功能（待实现）")
    void testJpaFindByName() {
        assertNotNull(userRepository, "JPA 仓储应该被注入");

        // TODO: 等待 JPA 实现完成
        System.out.println("⚠ JPA 查询功能待实现");
    }
}
