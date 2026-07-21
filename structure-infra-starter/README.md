# structure-infra-starter

`structure-pro-infra` 框架的核心模块，提供 DDD 风格的仓储抽象层（ACL/防腐层）、可插拔的委托式 CQRS 机制、事件发布抽象、轻量级任务调度集成，以及动态低代码资源仓储子系统。

## 模块定位

本模块是其他所有 `structure-infra-*-starter` 的依赖基础，定义了以下抽象：

- **仓储 Facade/Delegate 抽象**：领域层通过 `RepositoryFacade` 操作领域实体，底层由 `RepositoryDelegate` 与具体持久化技术交互
- **自动装配机制**：通过 `RepositoryBeanPostProcessor` 在启动时扫描 `@WriteDelegate` / `@ReadDelegate` 注解，自动匹配并注入委托
- **CQRS 读写分离**：支持为每个仓储配置 BASE 写代理和 READ 读代理，读操作失败自动回退到写代理
- **低代码仓储子系统**：通过 YAML 定义资源 schema，运行时路由到不同存储引擎，无需编写实体类
- **事件管理**：统一 `EventManager` 抽象，支持 Spring 事件和消息中间件两种通道
- **任务调度集成**：当 `structure-infra-schedule-starter` 未引入时，提供基础调度兜底实现

## 依赖

- `cn.structured:structure-common` — 提供 `ICrudRepository` / `IQueryRepository` / `ReqPage` / `ResPage`
- `cn.structured:structure-datascope-starter` — 数据权限基础
- `cn.structured:structure-datascope-message` — 提供 `DataScopeStreamBridge`（事件系统使用）
- `cn.structured:structure-datascope-cache` — 提供 `DataScopeCacheManager`
- `org.springframework.boot:spring-boot-data-commons`
- `cn.structured:structure-infra-schedule-starter` — 调度 SPI 与默认实现

## 自动配置

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册：

```
cn.structure.infra.configuration.AutoEventConfiguration
cn.structure.infra.configuration.AutoRepositoryConfiguration
cn.structure.infra.lowcode.configuration.LowCodeAutoConfiguration
```

> 注：`AutoScheduleConfiguration` 在本模块中作为兜底配置（当 `structure-infra-schedule-starter` 已注册自有 Bean 时不重复注册）。

## 包结构

```
cn.structure.infra
├── annotations/         @WriteDelegate / @ReadDelegate
├── configuration/       AutoEventConfiguration / AutoRepositoryConfiguration / AutoScheduleConfiguration
├── event/               Event / EventManager / EventChannel / DefaultEventManagerImpl
├── lowcode/             低代码子系统
│   ├── configuration/   LowCodeAutoConfiguration
│   ├── model/           ResourceSchema / FieldSchema / RepositoryConfig / CqrsConfig / CacheConfig
│   │                    StorageType / FieldType / AutoFillType
│   ├── properties/      LowCodeProperties
│   ├── registry/        ResourceSchemaBuilder
│   ├── repository/      LowCodeRepository / LowCodeStorage / LowCodeRepoFactory
│   └── router/          LowCodeRepositoryRouter
├── properties/          InfraProperties
└── repository/          仓储核心接口与实现
    ├── RepositoryDelegate.java
    ├── IQueryDelegate.java
    ├── RepositoryFacade.java
    ├── CqrsRepositoryFacade.java
    ├── RepositoryBeanPostProcessor.java
    ├── GenericTypeResolver.java
    ├── DelegateType.java
    ├── RepositoryType.java
    └── InMemoryRepositoryDelegate.java
```

## 仓储子系统

### RepositoryType

| 类型 | 说明 |
|-----|------|
| `MYBATIS` | MyBatis |
| `MYBATIS_PLUS` | MyBatis Plus |
| `JPA` | Spring Data JPA |
| `JDBC` | JDBC |
| `NOSQL` | 通用 NoSQL |
| `REDIS` | Redis |
| `MONGODB` | MongoDB |
| `ELASTICSEARCH` | Elasticsearch |
| `AUTO` | 自动检测（使用第一个能创建 delegate 的工厂） |

### DelegateType

| 类型 | 说明 |
|-----|------|
| `BASE` | 基础代理：承担写操作和默认读操作 |
| `READ` | 读代理：仅承担读操作（CQRS 模式下使用） |

### 核心接口

#### `RepositoryDelegate<T, ID>`

继承自 `ICrudRepository<T, ID>` 与 `IQueryDelegate<T, ID>`，定义对领域实体的完整 CRUD 操作。各持久化 starter 提供具体实现。

**关键方法**：
- `getEntityClass()` - 获取领域实体类型
- `getPoClass()` - 获取持久化对象类型（PO）
- `getIdClass()` - 获取主键类型
- `getIdFieldName()` - 获取 ID 字段名称

#### `IQueryDelegate<T, ID>`

只读 delegate 契约，定义 `findById` / `listByIds` / `count` / `exists`。可作为 CQRS 读代理的契约。

#### `RepositoryDelegateFactory`

```java
RepositoryType getType();
RepositoryDelegate<?, ?> createDelegate(Class<?> poClass, Class<?> idClass);
```

每个持久化 starter 实现此 SPI，用于在无用户自定义 delegate 时自动创建。

#### `RepositoryFacade<T, ID, D extends RepositoryDelegate<T, ID>>`

用户侧门面，职责：

- 通过泛型参数指定领域实体类型、主键类型和委托类型
- 写操作（`save` / `removeById` / `saveBatch` / `removeBatchByIds`）始终走 `delegate`
- 读操作（`queryById` / `queryOne` / `queryList` / `queryPage` / `listByIds` / `count` / `exists`）走 `delegate`
- `findById` 始终走 `delegate`（严格的实体查询）

#### `CqrsRepositoryFacade<T, ID, D extends RepositoryDelegate<T, ID>, RD extends IQueryDelegate<T, ID>>`

CQRS 模式门面，继承自 `RepositoryFacade`：

- 维护两个代理：`delegate`（写代理）和 `readDelegate`（读代理）
- 写操作始终走 `delegate`（通过父类字段）
- 读操作优先走 `readDelegate`，失败回退到 `delegate`
- 通过 `RepositoryBeanPostProcessor` 根据泛型类型和注解标记自动注入

#### `InMemoryRepositoryDelegate<T, ID>`

默认兜底实现，使用 `ConcurrentHashMap` 与 `AtomicLong` ID 生成器，主要用于测试或无任何持久化 starter 的场景。

### 注解

#### `@WriteDelegate`

标注在 `RepositoryDelegate` 实现类上，标记为写代理/基础代理：

```java
@Qualifier
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WriteDelegate {
}
```

#### `@ReadDelegate`

标注在 `IQueryDelegate` / `RepositoryDelegate` 实现类上，标记为读代理：

```java
@Qualifier
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ReadDelegate {
}
```

### 委托匹配策略

`RepositoryBeanPostProcessor` 在 Bean 初始化后执行匹配：

1. **解析泛型签名**：从 `RepositoryFacade` / `CqrsRepositoryFacade` 子类的泛型参数中提取委托类型
2. **写代理搜索**（按优先级）：
   1. 类型匹配 + `@WriteDelegate` 标记
   2. 类型匹配 + 无 `@ReadDelegate` 标记
   3. 类型匹配（兜底）
3. **读代理搜索**（仅 `CqrsRepositoryFacade`）：
   1. 类型匹配 + `@ReadDelegate` 标记
   2. 类型匹配（兜底）

## CQRS 读写分离

### 类型化 CQRS（CqrsRepositoryFacade）

```java
@Component("userCqrsRepository")
public class UserCqrsRepository 
    extends CqrsRepositoryFacade<UserEntity, Long, UserWriteDelegate, UserReadDelegate> 
    implements UserRepository {

    @Override
    public UserEntity findByName(String name) {
        return getReadDelegate().findByName(name);
    }
}

@Slf4j
@Component
@WriteDelegate
public class UserWriteDelegate 
    extends MybatisPlusRepositoryDelegate<UserEntity, MybatisUserPO, Long> 
    implements UserRepositoryDelegate {
}

@Slf4j
@Component
@ReadDelegate
public class UserReadDelegate 
    extends ElasticsearchRepositoryDelegate<UserEntity, MybatisUserPO, Long> 
    implements UserRepositoryDelegate {
}
```

读操作执行流程：
- `readDelegate != null` → 尝试执行读操作
- 异常 → 记录 warning，回退到 `delegate` 兜底
- `findById` 与写操作始终走 `delegate`

### 单代理模式（RepositoryFacade）

```java
@Component("userRepository")
public class UserRepositoryImpl 
    extends RepositoryFacade<UserEntity, Long, UserMybatisPlusDelegate> 
    implements UserRepository {
}

@Slf4j
@Component
public class UserMybatisPlusDelegate 
    extends MybatisPlusRepositoryDelegate<UserEntity, MybatisUserPO, Long> 
    implements UserRepositoryDelegate {
}
```

### 低代码 CQRS（LowCodeRepositoryRouter）

YAML 声明：

```yaml
structure:
  infra:
    lowcode:
      resources:
        order:
          schema:
            table-name: t_order
            fields:
              id: { type: long, primary-key: true, auto-increment: true }
              order_no: { type: string, length: 32, unique: true }
              amount: { type: decimal, precision: 12, scale: 2 }
          repository:
            type: mysql
            cqrs:
              enabled: true
              read-type: elasticsearch
              read-datasource: es-cluster
            cache:
              enabled: true
              ttl: 300
              time-unit: seconds
```

注册时同时创建 base storage 与 read storage；读操作先尝试 read storage，失败回退 base storage。

## 低代码子系统

### 数据模型

- `StorageType`：`MYSQL` / `MONGODB` / `ELASTICSEARCH` / `REDIS` / `IN_MEMORY`
- `FieldType`：`STRING` / `LONG` / `INTEGER` / `BOOLEAN` / `DECIMAL` / `DATETIME` / `DATE` / `OBJECT_ID` / `TEXT` / `JSON`
- `AutoFillType`：`NONE` / `CREATE` / `UPDATE` / `CREATE_UPDATE`
- `FieldSchema`：字段名、列名、类型、长度、精度、是否主键/自增/可空/唯一/索引、默认值、自动填充类型
- `ResourceSchema`：资源名、表名、ID 字段名、ID 类型、字段 Map
- `RepositoryConfig`：存储类型、数据源名、CQRS 子配置、缓存子配置

### 核心接口

#### `LowCodeRepository`（用户侧）

方法签名与 `ICrudRepository` 一致，但每个方法首参为 `String resourceName`，操作对象为 `Map<String, Object>`：

- 写：`save` / `removeById` / `saveBatch` / `removeBatchByIds` / `exists`
- 读：`queryById` / `queryByIdOptional` / `queryOne` / `queryOneOptional` / `queryList` / `queryPage` / `listByIds` / `count`
- `findById` 始终走 base storage

#### `LowCodeStorage`（引擎侧）

每个存储引擎实现此接口，方法集与 `LowCodeRepository` 一致（去掉 `resourceName` 参数），额外有 `void initialize()` 用于建表/集合/索引。

#### `LowCodeRepoFactory`

```java
StorageType getType();
LowCodeStorage createStorage(ResourceSchema schema, RepositoryConfig config);
```

各存储 starter 提供 factory bean，由 `LowCodeRepositoryRouter` 自动收集。

#### `LowCodeRepositoryRouter`

`LowCodeAutoConfiguration` 注册的唯一 bean。维护 `resourceName -> StorageHolder` 映射。注册资源时按 `RepositoryConfig.type` 选择 factory 创建 base storage，若开启 CQRS 则同步创建 read storage。

### YAML 配置示例

```yaml
structure:
  infra:
    lowcode:
      enabled: true
      resources:
        user:
          schema:
            table-name: t_user
            id-type: long
            fields:
              id:
                type: long
                primary-key: true
                auto-increment: true
              username:
                type: string
                length: 64
                nullable: false
                index: true
              email:
                type: string
                length: 128
                unique: true
              created_at:
                type: datetime
                auto-fill: create
              updated_at:
                type: datetime
                auto-fill: create_update
          repository:
            type: mysql
            datasource: master
```

字段支持的属性：`type` / `length` / `precision` / `scale` / `primary-key` / `auto-increment` / `nullable` / `unique` / `index` / `default-value` / `auto-fill` / `description`。

存储类型字符串解析（大小写不敏感）：
- `mysql` → `MYSQL`
- `mongodb` / `mongo` → `MONGODB`
- `elasticsearch` / `es` → `ELASTICSEARCH`
- `redis` → `REDIS`
- `memory` / `in_memory` → `IN_MEMORY`

字段类型字符串解析：`string` / `varchar` / `long` / `bigint` / `int` / `integer` / `bool` / `boolean` / `decimal` / `double` / `float` / `datetime` / `timestamp` / `date` / `objectid` / `object_id` / `text` / `json`。

### 运行时动态注册

```java
@Component
@RequiredArgsConstructor
public class RuntimeRegistrar implements ApplicationRunner {
    private final LowCodeRepositoryRouter router;

    @Override
    public void run(ApplicationArguments args) {
        ResourceSchema schema = ResourceSchema.builder()
            .resourceName("audit_log")
            .tableName("t_audit_log")
            .build();
        schema.addField(FieldSchema.builder()
            .name("id").type(FieldType.LONG).primaryKey(true).autoIncrement(true).build());
        schema.addField(FieldSchema.builder()
            .name("action").type(FieldType.STRING).length(64).nullable(false).build());

        RepositoryConfig cfg = new RepositoryConfig();
        cfg.setType(StorageType.MYSQL);
        cfg.setDatasource("master");

        router.registerResource("audit_log", schema, cfg);
    }
}
```

## 事件子系统

### 事件通道

| 通道 | 行为 |
|------|------|
| `DEFAULT` | 由 `InfraProperties.defaultEventChannel` 决定路由 |
| `SPRING_EVENT` | 通过 `ApplicationEventPublisher` 同步发布 |
| `MESSAGE_EVENT` | 通过 `DataScopeStreamBridge` 发送到消息中间件 |

### 接口

```java
public interface Event {
    String getEventId();
    default EventChannel getEventChannel() { return EventChannel.DEFAULT; }
}

public interface EventManager {
    void publish(Event event);
}
```

### 使用

```java
public class UserCreatedEvent implements Event {
    private final String eventId = UUID.randomUUID().toString();
    @Override public String getEventId() { return eventId; }
}

@Component
@RequiredArgsConstructor
public class UserEventPublisher {
    private final EventManager eventManager;
    public void publish() { eventManager.publish(new UserCreatedEvent()); }
}

@Component
public class UserEventListener {
    @EventListener
    public void on(UserCreatedEvent event) { /* ... */ }
}
```

> 注：`AutoEventConfiguration` 仅在已存在 `EventManager` bean 时注册 `DefaultEventManagerImpl`。需要使用事件功能时，请确保上下文中已有 `EventManager` bean（通常由 `DefaultEventManagerImpl` 自身或外部 starter 提供）。

## 配置属性

### InfraProperties（`structure.infra.*`）

| 属性 | 类型 | 默认值 | 说明 |
|-----|------|-------|------|
| `defaultEventChannel` | `EventChannel` | `SPRING_EVENT` | `DEFAULT` 事件的默认通道 |
| `cqrs` | `Boolean` | `false` | 全局 CQRS 开关（仅作建议） |
| `schedulePoolSize` | `Integer` | `Runtime.availableProcessors()` | 调度线程池大小 |
| `type` | `RepositoryType` | - | 默认持久化类型（用于触发各 starter 的条件装配） |

### LowCodeProperties（`structure.infra.lowcode.*`）

```yaml
structure:
  infra:
    lowcode:
      enabled: true                       # 默认 true
      resources:
        <resource-name>:
          schema:
            table-name: <table>
            id-type: long
            fields:
              <field-name>:
                type: string
                length: 255
                precision: 10
                scale: 2
                primary-key: false
                auto-increment: false
                nullable: true
                unique: false
                index: false
                default-value: <literal>
                auto-fill: none           # none|create|update|create_update
                description: <text>
          repository:
            type: mysql                   # mysql|mongodb|elasticsearch|redis|in_memory
            datasource: <name>
            cqrs:
              enabled: false
              read-type: elasticsearch
              read-datasource: <name>
            cache:
              enabled: false
              ttl: 300
              time-unit: seconds          # seconds|minutes|hours|days|milliseconds
```

## 完整使用示例

### 1. 定义领域实体与 PO

```java
// 领域实体（无任何持久化注解）
public class UserEntity {
    private Long id;
    private String username;
    private String email;
    // getters/setters
}

// 持久化对象（由具体存储技术的注解决定）
@TableName("t_user")
public class UserPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
}
```

### 2. 声明 RepositoryFacade（单代理模式）

```java
@Component("userRepository")
public class UserRepositoryImpl 
    extends RepositoryFacade<UserEntity, Long, UserMybatisPlusDelegate> 
    implements UserRepository {
}
```

### 3. 声明 CqrsRepositoryFacade（CQRS 模式）

```java
@Component("userCqrsRepository")
public class UserCqrsRepository 
    extends CqrsRepositoryFacade<UserEntity, Long, UserWriteDelegate, UserReadDelegate> 
    implements UserRepository {
    
    @Override
    public UserEntity findByName(String name) {
        return getReadDelegate().findByName(name);
    }
}
```

### 4. 提供 delegate 实现

```java
// 写代理（单代理模式或 CQRS 模式的写代理）
@Slf4j
@Component
@WriteDelegate
public class UserMybatisPlusDelegate 
    extends MybatisPlusRepositoryDelegate<UserEntity, UserPO, Long> 
    implements UserRepositoryDelegate {
    
    private final UserMapper userMapper;
    
    @Override
    public UserEntity findByName(String name) {
        UserPO po = userMapper.selectOne(
            Wrappers.<UserPO>lambdaQuery().eq(UserPO::getUsername, name));
        return toEntity(po);
    }
}

// 读代理（仅 CQRS 模式）
@Slf4j
@Component
@ReadDelegate
public class UserReadDelegate 
    extends ElasticsearchRepositoryDelegate<UserEntity, UserPO, Long> 
    implements UserRepositoryDelegate {
    
    @Override
    public UserEntity findByName(String name) {
        // Elasticsearch 查询实现
        return null;
    }
}
```

### 5. 业务层使用

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserCqrsRepository userCqrsRepository;

    public UserEntity create(String username, String email) {
        UserEntity u = new UserEntity();
        u.setUsername(username);
        u.setEmail(email);
        return userRepository.save(u);
    }

    public Optional<UserEntity> findByUsername(String username) {
        UserEntity probe = new UserEntity();
        probe.setUsername(username);
        return userRepository.queryOneOptional(probe);
    }

    public ResPage<UserEntity> page(int page, int size) {
        ReqPage reqPage = new ReqPage();
        reqPage.setPage(page);
        reqPage.setSize(size);
        // CQRS 模式：读操作走 readDelegate
        return userCqrsRepository.queryPage(reqPage);
    }
}
```

### 6. 低代码使用

```java
@Service
@RequiredArgsConstructor
public class DynamicResourceService {
    private final LowCodeRepository lowCodeRepository;

    public Map<String, Object> create(String username, String email) {
        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("email", email);
        return lowCodeRepository.save("user", data);
    }
}
```

## 测试

```bash
mvn test -pl structure-infra-starter
```

## License

Apache License 2.0