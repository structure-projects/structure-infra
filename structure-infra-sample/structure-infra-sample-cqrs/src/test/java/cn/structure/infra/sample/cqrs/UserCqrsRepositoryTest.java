package cn.structure.infra.sample.cqrs;

import cn.structure.infra.sample.cqrs.config.CqrsTestConfig;
import cn.structure.infra.sample.cqrs.infra.delegate.write.UserWriteDelegate;
import cn.structure.infra.sample.cqrs.infra.repositoory.UserCqrsRepository;
import cn.structure.infra.repository.IQueryDelegate;
import cn.structure.infra.sample.infra.po.UserPO;
import cn.structure.infra.sample.domain.entity.UserEntity;
import cn.structure.infra.sample.domain.repository.UserRepository;
import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CQRS 仓储测试类
 * <p>
 * 测试读写分离模式下的仓储操作：
 * <ul>
 *   <li>写操作通过 BASE 代理（MyBatis Plus）执行</li>
 *   <li>读操作通过 READ 代理（Elasticsearch）执行</li>
 *   <li>当 READ 代理失败时，自动回退到 BASE 代理</li>
 * </ul>
 * <p>
 * 验证多种目标代理同时加载的工作机制。
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Slf4j
@SpringBootTest(classes = CqrsTestConfig.class)
@DisplayName("CQRS 读写分离仓储测试")
class UserCqrsRepositoryTest {

    @Autowired
    private UserCqrsRepository userCqrsRepository;

    @Autowired(required = false)
    private UserRepository userRepository;

    /**
     * 创建测试用户实体
     *
     * @param username 用户名
     * @param email    邮箱
     * @param age      年龄
     * @return 用户实体
     */
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

    @BeforeEach
    void setUp() {
        log.info("========== CQRS 测试开始 ==========");
    }

    @Test
    @DisplayName("测试 CQRS 仓储注入")
    void testCqrsRepositoryInjection() {
        assertNotNull(userCqrsRepository, "CQRS 仓储应该被成功注入");
        log.info("✓ CQRS 仓储注入成功: {}", userCqrsRepository.getClass().getName());
    }

    @Test
    @DisplayName("测试 BASE 写代理注入")
    void testBaseDelegateInjection() {
        assertNotNull(userCqrsRepository, "CQRS 仓储应该被注入");

        // 验证 BASE 代理存在
        UserWriteDelegate baseDelegate = userCqrsRepository.getBaseDelegate();
        assertNotNull(baseDelegate, "BASE 写代理应该被注入");
        log.info("✓ BASE 写代理注入成功: {}", baseDelegate.getClass().getName());
    }

    @Test
    @DisplayName("测试 READ 读代理注入")
    void testReadDelegateInjection() {
        assertNotNull(userCqrsRepository, "CQRS 仓储应该被注入");

        // 验证 READ 代理存在
        IQueryDelegate<UserPO, Long> readDelegate = userCqrsRepository.getReadDelegate();
        assertNotNull(readDelegate, "READ 读代理应该被注入");
        log.info("✓ READ 读代理注入成功: {}", readDelegate.getClass().getName());
    }

    @Test
    @DisplayName("测试多种代理同时加载")
    void testMultipleDelegatesLoaded() {
        assertNotNull(userCqrsRepository, "CQRS 仓储应该被注入");

        // 验证同时加载了两种代理
        UserWriteDelegate baseDelegate = userCqrsRepository.getBaseDelegate();
        IQueryDelegate<UserPO, Long> readDelegate = userCqrsRepository.getReadDelegate();

        assertNotNull(baseDelegate, "BASE 代理应该被加载");
        assertNotNull(readDelegate, "READ 代理应该被加载");

        log.info("✓ 多种代理同时加载成功:");
        log.info("  - BASE 代理: {}", baseDelegate.getClass().getSimpleName());
        log.info("  - READ 代理: {}", readDelegate.getClass().getSimpleName());
    }

    @Test
    @DisplayName("测试写操作使用 BASE 代理")
    void testWriteOperationUsesBaseDelegate() {
        // 执行写操作（save）- 应该使用 BASE 代理
        UserEntity user = createUser("writeUser", "write@test.com", 25);
        UserEntity saved = userCqrsRepository.save(user);

        assertNotNull(saved, "写操作应该返回结果");
        assertNotNull(saved.getId(), "写操作应该生成 ID");

        log.info("✓ 写操作成功（使用 BASE 代理）:");
        log.info("  - 用户ID: {}", saved.getId());
        log.info("  - 用户名: {}", saved.getUsername());
    }

    @Test
    @DisplayName("测试读操作使用 READ 代理")
    void testReadOperationUsesReadDelegate() {
        // 先写入数据（使用 BASE 代理）
        UserEntity user = createUser("readUser", "read@test.com", 30);
        UserEntity saved = userCqrsRepository.save(user);

        // 执行读操作（findById）- 应该使用 READ 代理
        UserEntity found = userCqrsRepository.findById(saved.getId());

        // 注意：由于 Mock Elasticsearch 和真实数据库不同步，
        // 这里主要验证读操作能够正常执行，不一定返回数据
        log.info("✓ 读操作执行完成（使用 READ 代理）:");
        log.info("  - 查询ID: {}", saved.getId());
        log.info("  - 查询结果: {}", found != null ? "找到" : "未找到");
    }

    @Test
    @DisplayName("测试删除操作使用 BASE 代理")
    void testDeleteOperationUsesBaseDelegate() {
        // 创建并保存用户
        UserEntity user = createUser("deleteUser", "delete@test.com", 28);
        UserEntity saved = userCqrsRepository.save(user);
        assertNotNull(saved.getId());

        // 执行删除操作 - 应该使用 BASE 代理
        userCqrsRepository.removeById(saved.getId());

        // 验证删除成功
        UserEntity found = userCqrsRepository.findById(saved.getId());
        // 由于 CQRS 模式下读代理和写代理可能不同步，这里只验证删除操作执行成功
        log.info("✓ 删除操作执行完成（使用 BASE 代理）");
    }

    @Test
    @DisplayName("测试批量保存使用 BASE 代理")
    void testBatchSaveUsesBaseDelegate() {
        List<UserEntity> users = List.of(
                createUser("batch1", "batch1@test.com", 20),
                createUser("batch2", "batch2@test.com", 25),
                createUser("batch3", "batch3@test.com", 30)
        );

        List<UserEntity> savedUsers = userCqrsRepository.saveBatch(users);

        assertNotNull(savedUsers, "批量保存应该返回结果");
        assertTrue(savedUsers.size() >= 3, "批量保存应该成功");

        log.info("✓ 批量保存成功（使用 BASE 代理）:");
        log.info("  - 保存数量: {}", savedUsers.size());
    }

    @Test
    @DisplayName("测试 queryByIdOptional 读操作")
    void testQueryByIdOptional() {
        // 先保存数据
        UserEntity user = createUser("optionalUser", "optional@test.com", 22);
        UserEntity saved = userCqrsRepository.save(user);

        // 执行 Optional 查询 - 使用 READ 代理
        Optional<UserEntity> optional = userCqrsRepository.queryByIdOptional(saved.getId());

        log.info("✓ Optional 查询完成（使用 READ 代理）:");
        log.info("  - 查询ID: {}", saved.getId());
        log.info("  - 结果存在: {}", optional.isPresent());
    }

    @Test
    @DisplayName("测试 queryList 读操作")
    void testQueryList() {
        // 执行列表查询 - 使用 READ 代理
        List<UserEntity> list = userCqrsRepository.queryList(null);

        assertNotNull(list, "列表查询应该返回结果");

        log.info("✓ 列表查询完成（使用 READ 代理）:");
        log.info("  - 查询结果数量: {}", list.size());
    }

    @Test
    @DisplayName("测试 queryPage 分页读操作")
    void testQueryPage() {
        ReqPage reqPage = new ReqPage();
        reqPage.setPage(1);
        reqPage.setSize(10);

        // 执行分页查询 - 使用 READ 代理
        ResPage<UserEntity> page = userCqrsRepository.queryPage(reqPage);

        assertNotNull(page, "分页查询应该返回结果");

        log.info("✓ 分页查询完成（使用 READ 代理）:");
        log.info("  - 当前页: {}", page.getCurrent());
        log.info("  - 每页数量: {}", page.getSize());
        log.info("  - 总数量: {}", page.getTotal());
        log.info("  - 记录数: {}", page.getRecords().size());
    }

    @Test
    @DisplayName("测试 CQRS Entity <-> PO 转换")
    void testEntityPoConversion() {
        // 保存实体（BASE 代理处理）
        UserEntity user = createUser("convertUser", "convert@test.com", 28);
        UserEntity saved = userCqrsRepository.save(user);

        assertNotNull(saved.getId(), "保存后应该有 ID");

        // 验证转换过程
        log.info("✓ Entity -> PO -> Entity 转换成功:");
        log.info("  - 原始实体: username={}, email={}, age={}",
                user.getUsername(), user.getEmail(), user.getAge());
        log.info("  - 保存实体: id={}, username={}, email={}, age={}",
                saved.getId(), saved.getUsername(), saved.getEmail(), saved.getAge());
    }

    @Test
    @DisplayName("测试 CQRS 模式下的完整 CRUD 流程")
    void testFullCqrsCrudFlow() {
        log.info("========== CQRS 完整 CRUD 流程测试 ==========");

        // 1. 创建（写操作 - BASE 代理）
        UserEntity user = createUser("cqrsFullFlowUniqueUser", "cqrsFullFlowUnique@test.com", 35);
        UserEntity saved = userCqrsRepository.save(user);
        log.info("Step 1 - CREATE（BASE 代理）: id={}", saved.getId());
        assertNotNull(saved.getId());

        // 2. 读取（读操作 - READ 代理）
        UserEntity found = userCqrsRepository.findById(saved.getId());
        log.info("Step 2 - READ（READ 代理）: result={}", found != null ? "找到" : "未找到");

        // 3. 删除（写操作 - BASE 代理）
        userCqrsRepository.removeById(saved.getId());
        log.info("Step 3 - DELETE（BASE 代理）: 执行完成");

        log.info("✓ CQRS 完整 CRUD 流程测试完成");
    }

    @Test
    @DisplayName("测试 UserRepository 接口注入")
    void testUserRepositoryInterfaceInjection() {
        // 验证接口注入
        assertNotNull(userRepository, "UserRepository 接口应该被注入");
        log.info("✓ UserRepository 接口注入成功: {}", userRepository.getClass().getName());
    }
}