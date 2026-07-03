# structure-infra-jpa-starter

[JPA (Jakarta Persistence API) / Hibernate](https://spring.io/projects/spring-data-jpa) 接入 `structure-pro-infra` 仓储抽象层的适配模块，让领域仓储（`RepositoryFacade`）透明地通过 JPA `EntityManager` 完成 CRUD。

## 模块定位

本模块在 `structure-infra-starter` 的 `RepositoryDelegate` SPI 之上提供 JPA 实现，作为领域层与持久化层之间的桥梁：

- **领域层**：纯粹的领域实体（`UserEntity`）与仓储接口（`UserRepository`），无任何 JPA 注解
- **持久化层**：JPA 注解的 PO（`UserPO`），通过 `EntityManager` 操作

## 功能特性

- **Spring Boot 自动配置**：通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册
- **条件激活**：仅当 classpath 存在 `JpaRepository` 且上下文有 `EntityManager` bean 时激活
- **启用 Spring Data JPA**：自动应用 `@EnableJpaRepositories` 与 `@EnableTransactionManagement`
- **自动创建 delegate**：`JpaDelegateFactory` 按需为任意 PO 类实例化 `JpaRepositoryDelegate`
- **自动注入 EntityManager**：`JpaDelegateBeanPostProcessor` 发现用户自定义的 `JpaRepositoryDelegate` bean，注入 `EntityManager` 与从 `@DelegateFor.po()` 解析的实体类
- **完整 CRUD + 分页 + 条件查询**：基于 JPA Criteria API 实现，通过反射读取条件对象的非空字段构建等值谓词
- **CQRS 兼容**：可作为 BASE 代理或 READ 代理，与 `RepositoryFacade` 的读写分离机制无缝配合

## 依赖

```xml
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-jpa-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

模块依赖极简：

- `cn.structured:structure-infra-starter` — 核心框架（`RepositoryFacade` / `RepositoryDelegate` / `RepositoryDelegateFactory` / 注解 / `RepositoryBeanPostProcessor`）
- `org.springframework.boot:spring-boot-starter-data-jpa` — Spring Data JPA + Hibernate + Jakarta Persistence API

## 自动配置

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册：

```
cn.structure.infra.jpa.configuration.JpaAutoConfiguration
```

`JpaAutoConfiguration`：

- `@ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")`
- `@EnableJpaRepositories` + `@EnableTransactionManagement`
- 注册 `jpaDelegateFactory(EntityManager)`：`@ConditionalOnBean(EntityManager.class)`
- 注册 `jpaDelegateBeanPostProcessor()`：`@ConditionalOnClass(name = "jakarta.persistence.EntityManager")`

## 核心类

### `JpaAutoConfiguration`

Spring Boot 自动配置入口，注册两个 Bean：

| Bean | 类型 | 用途 |
|------|------|------|
| `jpaDelegateFactory` | `JpaDelegateFactory` | 按需创建 `JpaRepositoryDelegate` 实例 |
| `jpaDelegateBeanPostProcessor` | `JpaDelegateBeanPostProcessor` | 向用户自定义的 `JpaRepositoryDelegate` 注入 `EntityManager` 与实体类 |

### `JpaDelegateFactory`

实现 `RepositoryDelegateFactory`：

- `getType()` 返回 `RepositoryType.JPA`
- `createDelegate(poClass, idClass)` 构造 `new JpaRepositoryDelegate(entityManager, poClass)`，构造失败返回 `null`

### `JpaRepositoryDelegate<T, ID>`

实现 `RepositoryDelegate<T, ID>`，基于 `EntityManager` 与 JPA Criteria API：

- **构造**：无参构造（便于子类 `@DelegateFor` 标注）+ setter；或全参构造 `JpaRepositoryDelegate(EntityManager, Class<T>)`
- **写操作**：`save` 使用 `entityManager.merge(entity)`；`removeById` / `removeBatchByIds` 先 find 再 remove
- **读操作**：`queryList(condition)` 使用 Criteria API，通过反射读取条件对象非空字段（含父类）构建等值谓词
- **分页**：`queryPage` 在 `findAll()` 结果上做内存分页（适用于小数据集/测试场景）
- **count / exists**：基于 `queryList(condition).size()` 实现

### `JpaDelegateBeanPostProcessor`

`BeanPostProcessor` + `ApplicationContextAware`，对每个 `instanceof JpaRepositoryDelegate` 的 bean：

1. **解析 EntityManager**（按优先级）：
   - 查找名为 `"entityManager"` 的 bean 且 `instanceof EntityManager`
   - 否则查找 `EntityManagerFactory` bean，调用 `createEntityManager()` 创建新实例
   - 都失败则记录 warning
2. 调用 `delegate.setEntityManager(entityManager)`
3. 读取 `@DelegateFor` 注解，若 `po()` 不为 `void.class`，调用 `delegate.setEntityClass(annotation.po())`

支持两种使用模式：

- **模式 A（自动创建）**：用户不定义任何 delegate bean，`RepositoryBeanPostProcessor` 调用 `JpaDelegateFactory.createDelegate(poClass, idClass)` 构造已注入依赖的 `JpaRepositoryDelegate`
- **模式 B（用户自定义）**：用户继承 `JpaRepositoryDelegate` 并标注 `@DelegateFor(po = UserPO.class)`，后置处理器自动注入 `EntityManager` 与 `entityClass`

## 配置属性

本模块不定义专属配置属性。框架级配置：

```yaml
structure:
  infra:
    type: JPA                          # 触发 JpaAutoConfiguration 条件装配
```

标准 Spring Boot JPA / DataSource 配置：

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:jpa_testdb
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    cn.structure.infra.repository: DEBUG
    cn.structure.infra.jpa: DEBUG
```

## 使用示例

### 1. 定义领域实体（无 JPA 注解）

```java
@Data
public class UserEntity {
    private Long id;
    private String username;
    private String password;
    private String email;
    private Integer age;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

### 2. 定义领域仓储接口

```java
public interface UserRepository extends ICrudRepository<UserEntity, Long> {
    UserEntity findByName(String name);
}
```

### 3. 定义 JPA 注解的 PO

```java
@Data
@Entity
@Table(name = "t_user")
public class UserPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String password;
    private String email;
    private Integer age;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

### 4. 定义 delegate 接口（可选，用于自定义查询）

```java
public interface UserRepositoryDelegate extends RepositoryDelegate<UserPO, Long> {
    UserPO finByName(String name);
}
```

### 5. 定义抽象 Facade 基类

```java
public abstract class AbstractUserRepositoryImpl
        extends RepositoryFacade<UserEntity, Long, UserPO, UserRepositoryDelegate>
        implements UserRepository {

    @Override
    public UserEntity findByName(String name) {
        UserPO po = this.baseDelegate.finByName(name);
        return this.toEntity(po);
    }
}
```

### 6. 声明具体 JPA 仓储（标注 `@Repository`）

```java
@Repository(value = "用户仓储", type = RepositoryType.JPA,
            entity = UserEntity.class, po = UserPO.class)
@Component("userRepository")
public class UserJpaRepositoryImpl extends AbstractUserRepositoryImpl {
}
```

仓储体为空 — 所有 CRUD 行为由继承的 `RepositoryFacade` 与自动创建的 `JpaRepositoryDelegate` 提供。用户只需通过 `@Repository` 注解声明类型元数据。

### 7. 业务层使用

```java
@Autowired
private UserRepository userRepository;

UserEntity saved = userRepository.save(user);
UserEntity found = userRepository.findById(saved.getId());
List<UserEntity> list = userRepository.queryList(condition);
ResPage<UserEntity> page = userRepository.queryPage(reqPage);
```

`RepositoryFacade` 透明地：

1. 通过 `BeanUtils.copyProperties` 转换 `UserEntity` → `UserPO`
2. 调用 BASE delegate 的 JPA 操作（`entityManager.merge(po)` 等）
3. 将结果 `UserPO` 转换回 `UserEntity`

### 8. 提供测试用 `EntityManager` Bean

在测试环境中，通常需要显式暴露 `EntityManager`：

```java
@Bean
public EntityManager entityManager(EntityManagerFactory entityManagerFactory) {
    return SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
}
```

生产环境中，Spring Boot JPA 基础设施通常已注册名为 `entityManager` 的 bean，后置处理器可直接通过 bean 名查找。

## 端到端装配流程

1. Spring Boot 启动发现 `JpaAutoConfiguration`（通过 `AutoConfiguration.imports`）
2. `JpaAutoConfiguration` 激活（`JpaRepository` 在 classpath 上）
3. `EntityManager` bean 就绪后，`JpaDelegateFactory` 注册为 `RepositoryDelegateFactory` 类型为 `JPA`
4. `JpaDelegateBeanPostProcessor` 注册
5. 用户的 `@Repository(type = JPA, po = UserPO.class)` 注解类实例化为 `RepositoryFacade` 子类
6. `structure-infra-starter` 中的 `RepositoryBeanPostProcessor` 在 `ContextRefreshedEvent` 触发：
   - 查找用户定义的 `@DelegateFor` BASE delegate；若未找到，调用 `JpaDelegateFactory.createDelegate(UserPO.class, Long.class)` 返回完整构造的 `JpaRepositoryDelegate(entityManager, UserPO.class)`
   - 通过 `facade.setBaseDelegate(delegate)` 注入
7. 若用户定义了 `JpaRepositoryDelegate` 子类并标注 `@DelegateFor(po = UserPO.class)`，`JpaDelegateBeanPostProcessor` 在初始化后处理：
   - 解析并注入 `EntityManager`
   - 从 `@DelegateFor.po()` 读取并注入 `entityClass`
8. 应用调用 `userRepository.save(entity)` → `RepositoryFacade` 转换为 PO → `JpaRepositoryDelegate.save(po)` → `entityManager.merge(po)` → 转换回 entity

## 注意事项

- **`queryPage` 性能**：当前实现为先 `findAll()` 再内存分页，会加载全表数据。适用于小数据集与测试场景；生产大数据量场景建议自定义 delegate 使用 Criteria 的 `setFirstResult` / `setMaxResults` 与 count 查询
- **PO 复用**：同一 PO 类可同时标注 JPA / MyBatis-Plus / MongoDB / Elasticsearch 注解，在不同存储示例间复用（polyglot persistence）
- **Jakarta 命名空间**：Spring Boot 4.x 使用 `jakarta.persistence.*`，而非 `javax.persistence.*`
- **事务**：`@EnableTransactionManagement` 已启用，建议在 service 层标注 `@Transactional`

## 测试

参考示例模块 `structure-infra-sample-jpa`（使用 H2 内存数据库）：

```bash
mvn test -pl structure-infra-sample/structure-infra-sample-jpa
```

测试覆盖：

| 测试方法 | 操作 |
|---------|------|
| `testSave` | `save` |
| `testFindById` | `findById` |
| `testQueryById` | `queryById` |
| `testQueryByIdOptional_Exists` / `_NotExists` | `queryByIdOptional` |
| `testQueryOne` | `queryOne` 条件查询 |
| `testQueryOneOptional` / `_NotExists` | `queryOneOptional` |
| `testQueryList_All` / `_ByCondition` / `_Empty` | `queryList` |
| `testQueryPage` | `queryPage` 分页 |
| `testRemoveById` | `removeById` |
| `testEntityPoConversion` | Entity ↔ PO 转换验证 |
| `testSaveBatch` | `saveBatch` |
| `testRemoveBatchByIds` | `removeBatchByIds` |
| `testListByIds` | `listByIds` |
| `testCount_All` / `_ByCondition` | `count` |
| `testExists_True` / `_False` | `exists` |

## License

Apache License 2.0
