# structure-pro-infra

基于 DDD（领域驱动设计）理念的基础设施抽象层，提供统一的仓储接口和多种持久化技术的适配实现。

## 项目简介

该项目实现了一个基于 **Facade + Delegate** 模式的仓储抽象层，作为领域层与持久化层之间的防腐层（ACL），核心目标是：

- **解耦领域模型与持久化技术**：领域层只依赖统一的仓储接口，不关心底层使用哪种数据库
- **支持多持久化技术**：通过委托模式自动适配 MyBatis Plus、JPA、MongoDB、Elasticsearch 等
- **自动配置**：基于 Spring Boot AutoConfiguration 实现开箱即用
- **Entity-PO 自动转换**：RepositoryFacade 自动完成领域实体与持久化对象的转换
- **CQRS 读写分离**：支持一个仓储配置多个代理，写操作走基础代理，读操作走读代理
- **低代码仓储**：无需定义实体类，通过资源名称和 Map 动态操作数据，支持运行时动态注册

## 模块结构

```
structure-pro-infra/
├── structure-infra-starter/           # 核心模块
│   ├── annotations/                   # 注解定义
│   │   ├── Repository.java            # @Repository 注解
│   │   └── DelegateFor.java           # @DelegateFor 注解
│   ├── configuration/                 # 自动配置
│   ├── repository/                    # 仓储核心接口
│   │   ├── RepositoryFacade.java      # 仓储门面（对外）
│   │   ├── RepositoryDelegate.java    # 仓储委托（对内）
│   │   ├── RepositoryDelegateFactory.java # 委托工厂接口
│   │   ├── RepositoryType.java        # 仓储类型枚举
│   │   ├── DelegateType.java          # 委托类型枚举（BASE/READ）
│   │   └── InMemoryRepositoryDelegate.java # 内存实现（开发/测试用）
│   ├── lowcode/                       # 低代码仓储
│   │   ├── repository/                # 低代码仓储接口
│   │   │   ├── LowCodeRepository.java # 低代码统一仓储接口（用户侧）
│   │   │   ├── LowCodeStorage.java    # 低代码存储接口（引擎侧）
│   │   │   └── LowCodeRepoFactory.java # 低代码仓储工厂接口
│   │   ├── router/                    # 路由引擎
│   │   │   └── LowCodeRepositoryRouter.java # 低代码仓储路由器
│   │   ├── model/                     # 模型定义
│   │   │   ├── ResourceSchema.java    # 资源 schema
│   │   │   ├── FieldSchema.java       # 字段 schema
│   │   │   ├── StorageType.java       # 存储类型枚举
│   │   │   ├── FieldType.java         # 字段类型枚举
│   │   │   ├── AutoFillType.java      # 自动填充类型枚举
│   │   │   └── RepositoryConfig.java  # 仓储配置
│   │   └── registry/                  # 注册与构建
│   │       └── ResourceSchemaBuilder.java # 资源 schema 构建器
│   └── event/                         # 事件管理
├── structure-infra-mybatis-plus-starter/ # MyBatis Plus 适配（含低代码实现）
├── structure-infra-jpa-starter/        # JPA 适配
├── structure-infra-mongodb-starter/    # MongoDB 适配（含低代码实现）
├── structure-infra-elasticsearch-starter/ # Elasticsearch 适配（含低代码实现）
└── structure-infra-sample/             # 示例模块
    ├── structure-infra-sample-core/        # 共享核心（Entity、PO、Repository接口）
    ├── structure-infra-sample-mybatis/     # MyBatis Plus 示例（含低代码测试）
    ├── structure-infra-sample-jpa/         # JPA 示例
    ├── structure-infra-sample-mongodb/     # MongoDB 示例（含低代码测试）
    ├── structure-infra-sample-elasticsearch/ # Elasticsearch 示例（含低代码测试）
    └── structure-infra-sample-cqrs/        # CQRS 读写分离示例
```

## 核心概念

### RepositoryFacade

仓储门面，是领域层直接调用的接口，负责：

- 定义统一的 CRUD 操作契约
- 自动完成 Entity（领域实体）与 PO（持久化对象）的转换
- 内部持有 RepositoryDelegate 进行实际的持久化操作
- 支持 CQRS 模式：持有 baseDelegate（写）和 readDelegate（读）两个代理

### RepositoryDelegate

仓储委托，是持久化层的实现接口，负责：

- 直接操作 PO（持久化对象）
- 与具体的持久化技术交互（MyBatis Plus、JPA、MongoDB 等）
- 不同持久化技术提供各自的实现

### RepositoryDelegateFactory

委托工厂，用于自动创建 RepositoryDelegate 实例：

- 各个持久化技术的 starter 模块实现此接口
- 当找不到用户自定义的 delegate 时，通过工厂自动创建

### RepositoryType

仓储类型枚举，支持的类型：

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
| `AUTO` | 自动检测 |

### DelegateType

委托类型枚举，用于区分读写代理：

| 类型 | 说明 |
|-----|------|
| `BASE` | 基础代理，承担写操作和默认读操作 |
| `READ` | 读代理，专门承担读操作（CQRS 模式下使用） |

### 低代码仓储

低代码仓储是一套无需定义实体类和 PO 类的动态数据访问方案，通过资源名称和 `Map<String, Object>` 来操作数据。

**核心特点**：
- **零实体类**：无需定义 Java 实体类，通过 DSL/配置动态定义资源结构
- **动态注册**：支持运行时动态注册新资源，无需重启应用
- **多存储引擎**：同一套 API 支持 MySQL、MongoDB、Elasticsearch 等多种存储
- **自动建表**：资源注册时自动创建表/集合/索引
- **自动填充**：支持创建时间、更新时间等字段自动填充
- **统一路由**：通过 `LowCodeRepositoryRouter` 统一路由到对应存储引擎

**核心组件**：

| 组件 | 说明 |
|------|------|
| `LowCodeRepository` | 用户侧统一接口，方法名与 `ICrudRepository` 一致 |
| `LowCodeStorage` | 存储引擎侧接口，各存储引擎实现此接口 |
| `LowCodeRepoFactory` | 仓储工厂，创建具体的 `LowCodeStorage` 实例 |
| `LowCodeRepositoryRouter` | 路由引擎，根据资源名路由到对应存储 |
| `ResourceSchema` | 资源 schema 定义，描述资源的字段、索引等 |
| `FieldSchema` | 字段 schema 定义，描述单个字段的属性 |

**支持的存储类型**：

| 类型 | 实现模块 | 说明 |
|------|---------|------|
| `MYSQL` | structure-infra-mybatis-plus-starter | 基于 MyBatis Plus 实现 |
| `MONGODB` | structure-infra-mongodb-starter | 基于 MongoTemplate + Document 实现 |
| `ELASTICSEARCH` | structure-infra-elasticsearch-starter | 基于 ElasticsearchOperations + Map 实现 |
| `REDIS` | - | 规划中 |
| `IN_MEMORY` | - | 规划中（测试用） |

## 快速开始

### 示例模块

项目提供了完整的示例模块，包含 REST API 接口，可用于快速测试和学习：

**MongoDB 示例**（端口 8081）：
```bash
mvn spring-boot:run -pl structure-infra-sample/structure-infra-sample-mongodb
```

**Elasticsearch 示例**（端口 8082）：
```bash
mvn spring-boot:run -pl structure-infra-sample/structure-infra-sample-elasticsearch
```

**REST API 接口**（两个示例模块接口一致）：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/users` | 创建用户 |
| GET | `/api/users/{id}` | 根据ID查询 |
| GET | `/api/users/list` | 查询全部用户列表 |
| GET | `/api/users/page?page=1&size=10` | 分页查询 |
| PUT | `/api/users/{id}` | 更新用户 |
| DELETE | `/api/users/{id}` | 删除用户 |
| POST | `/api/users/batch` | 批量创建 |
| GET | `/api/users/count` | 查询总数 |

详细示例模块说明请参考 [SAMPLE_MODULES.md](./SAMPLE_MODULES.md)。

### 1. 添加依赖

根据需要选择对应的 starter：

```xml
<!-- 核心模块 -->
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- MyBatis Plus 适配 -->
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-mybatis-plus-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- JPA 适配 -->
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-jpa-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- MongoDB 适配 -->
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-mongodb-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- Elasticsearch 适配 -->
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-elasticsearch-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 定义领域实体和持久化对象

```java
// 领域实体
public class User {
    private Long id;
    private String name;
    private String email;
    // getters and setters
}

// 持久化对象（PO）
public class UserPO {
    private Long id;
    private String name;
    private String email;
    // getters and setters
}
```

### 3. 创建 RepositoryFacade

```java
@Repository(entity = User.class, po = UserPO.class, type = RepositoryType.MYBATIS_PLUS)
public class UserRepository extends RepositoryFacade<User, Long, UserPO, MybatisPlusRepositoryDelegate<UserPO, Long>> {
    // 可添加自定义方法
}
```

### 4. 使用仓储

```java
@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    public User saveUser(User user) {
        return userRepository.save(user);
    }
    
    public User getUserById(Long id) {
        return userRepository.findById(id);
    }
    
    public List<User> findUsers(User condition) {
        return userRepository.queryList(condition);
    }
    
    public ResPage<User> findUsersPage(ReqPage reqPage) {
        return userRepository.queryPage(reqPage);
    }
}
```

### 5. CQRS 读写分离模式（可选）

当需要读写分离时，可以为同一个仓储配置两个代理。**必须同时满足 `cqrs=true` 和 `readDelegateClass` 指定才会启用读代理**。

**1) 启用 CQRS**

```java
@Repository(
    entity = User.class, 
    po = UserPO.class, 
    type = RepositoryType.MYBATIS_PLUS,
    cqrs = true,                                    // 启用 CQRS
    readDelegateClass = UserReadDelegate.class      // 指定读代理类
)
public class UserRepository extends RepositoryFacade<User, Long, UserPO, MybatisPlusRepositoryDelegate<UserPO, Long>> {
}
```

**2) 定义写代理（BASE）**

```java
@DelegateFor(
    name = "userRepository", 
    po = UserPO.class, 
    type = RepositoryType.MYBATIS_PLUS,
    delegateType = DelegateType.BASE  // 写代理
)
public class UserWriteDelegate extends MybatisPlusRepositoryDelegate<UserPO, Long> {
}
```

**3) 定义读代理（READ）**

```java
@DelegateFor(
    name = "userRepository", 
    po = UserPO.class, 
    type = RepositoryType.ELASTICSEARCH,
    delegateType = DelegateType.READ  // 读代理
)
public class UserReadDelegate extends ElasticsearchRepositoryDelegate<UserPO, Long> {
}
```

**读操作回退机制**：
- 写操作（save、removeById、saveBatch、removeBatchByIds）始终走 BASE 代理（MyBatis Plus）
- 读操作（findById、queryList、queryPage、count、exists 等）优先走 READ 代理（Elasticsearch）
- 如果 READ 代理执行失败（抛出异常），自动回退到 BASE 代理执行
- BASE 代理是最后的兜底，确保读操作始终可用

### 6. 低代码仓储使用

低代码仓储无需定义实体类，通过资源名称和 Map 操作数据。

**1) 定义资源 Schema**

```java
ResourceSchema schema = ResourceSchema.builder()
    .resourceName("article")
    .tableName("t_lowcode_article")
    .build();

schema.addField(FieldSchema.builder()
    .name("id")
    .type(FieldType.LONG)
    .primaryKey(true)
    .build());

schema.addField(FieldSchema.builder()
    .name("title")
    .type(FieldType.STRING)
    .length(200)
    .nullable(false)
    .build());

schema.addField(FieldSchema.builder()
    .name("author")
    .type(FieldType.STRING)
    .index(true)
    .build());

schema.addField(FieldSchema.builder()
    .name("created_at")
    .type(FieldType.DATETIME)
    .autoFill(AutoFillType.CREATE)
    .build());
```

**2) 注册资源并使用**

```java
@Autowired
private LowCodeRepositoryRouter lowCodeRepositoryRouter;

// 注册资源（通常在启动时或配置中完成）
RepositoryConfig config = new RepositoryConfig();
config.setType(StorageType.MONGODB);
lowCodeRepositoryRouter.registerResource("article", schema, config);

// 保存数据
Map<String, Object> article = new HashMap<>();
article.put("title", "Hello World");
article.put("author", "zhangsan");
Map<String, Object> saved = lowCodeRepositoryRouter.save("article", article);

// 查询数据
Map<String, Object> found = lowCodeRepositoryRouter.findById("article", 1L);

// 条件查询
Map<String, Object> params = new HashMap<>();
params.put("author", "zhangsan");
List<Map<String, Object>> list = lowCodeRepositoryRouter.queryList("article", params);

// 分页查询
ReqPage reqPage = new ReqPage();
reqPage.setPage(1);
reqPage.setSize(10);
ResPage<Map<String, Object>> page = lowCodeRepositoryRouter.queryPage("article", reqPage);
```

**3) 切换存储引擎**

只需修改 `RepositoryConfig` 的 `type` 即可切换存储引擎，业务代码无需修改：

```java
// 使用 MySQL
config.setType(StorageType.MYSQL);

// 使用 MongoDB
config.setType(StorageType.MONGODB);

// 使用 Elasticsearch
config.setType(StorageType.ELASTICSEARCH);
```

## 注解说明

### @Repository

标注在 `RepositoryFacade` 子类上，配置仓储属性：

| 属性 | 类型 | 默认值 | 说明 |
|-----|------|-------|------|
| `value` | String | "" | 仓储名称 |
| `type` | RepositoryType | AUTO | 仓储类型 |
| `entity` | Class | Object.class | 领域实体类 |
| `po` | Class | Object.class | 持久化对象类 |
| `id` | Class | Long.class | 主键类型 |
| `description` | String | "" | 仓储描述 |
| `cache` | boolean | false | 是否启用缓存 |
| `cacheTime` | long | 60 | 缓存时间 |
| `cacheTimeUnit` | TimeUnit | SECONDS | 缓存时间单位 |
| `cqrs` | boolean | false | 是否启用 CQRS 读写分离 |

### @DelegateFor

标注在自定义 `RepositoryDelegate` 实现类上，用于注册委托：

| 属性 | 类型 | 默认值 | 说明 |
|-----|------|-------|------|
| `name` | String | "" | 仓储名称（对应 RepositoryFacade 的 Bean 名称） |
| `type` | RepositoryType | AUTO | 存储类型 |
| `po` | Class | Object.class | 持久化对象类型 |
| `description` | String | "" | 描述 |
| `priority` | int | 0 | 优先级（数字越大优先级越高） |
| `delegateType` | DelegateType | BASE | 委托类型（BASE=写/默认，READ=读） |

## 配置项

```yaml
structure:
  infra:
    default-event-channel: SPRING_EVENT  # 默认事件通道：DEFAULT, SPRING_EVENT, MESSAGE_EVENT
    cqrs: false                           # 是否开启 CQRS
    cache-time: 60                        # 默认缓存时间
    cache-time-unit: SECONDS              # 默认缓存时间单位
```

## 扩展指南

### 自定义 RepositoryDelegate

当默认实现无法满足需求时，可以自定义委托实现：

```java
@DelegateFor(name = "userRepository", po = UserPO.class, type = RepositoryType.MYBATIS_PLUS)
public class UserMybatisPlusDelegate extends MybatisPlusRepositoryDelegate<UserPO, Long> {
    
    // 自定义查询方法
    public List<UserPO> findByEmailLike(String emailPattern) {
        QueryWrapper<UserPO> wrapper = new QueryWrapper<>();
        wrapper.like("email", emailPattern);
        return baseMapper.selectList(wrapper);
    }
}
```

### 新增持久化技术支持

1. 创建 `RepositoryDelegate` 实现类
2. 创建 `RepositoryDelegateFactory` 实现类
3. 创建 `XXXDelegateBeanPostProcessor` 用于注入依赖
4. 创建 `XXXAutoConfiguration` 自动配置类
5. 在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中注册配置类

## 委托匹配策略

`RepositoryBeanPostProcessor` 在应用启动时自动匹配 delegate 到 facade。

### BASE 代理匹配优先级

1. 匹配 delegate 类型 + 名称 + 仓储类型
2. 匹配 delegate 类型 + 仓储类型
3. 匹配 delegate 类型 + 名称
4. 匹配 delegate 类型
5. 匹配名称
6. 匹配 PO 类型
7. 自动创建（通过 RepositoryDelegateFactory）
8. 使用默认的 InMemoryRepositoryDelegate

### READ 代理匹配优先级（CQRS 启用时）

与 BASE 代理匹配策略相同，但只匹配 `delegateType = READ` 的委托。如果未找到 READ 代理，读操作会回退到使用 BASE 代理。

## 事件管理

项目提供事件发布能力：

```java
public interface Event {
    String getEventId();
    default EventChannel getEventChannel() {
        return EventChannel.DEFAULT;
    }
}

public interface EventManager {
    void publish(Event event);
}
```

## 技术栈

- Java 21+
- Spring Boot 4.0.6
- Spring Data JPA 3.3+
- MyBatis Plus 3.5.16
- Spring Data MongoDB
- Spring Data Elasticsearch

## License

Apache License 2.0