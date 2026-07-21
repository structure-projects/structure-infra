# structure-pro-infra

基于 DDD（领域驱动设计）理念的基础设施抽象层，提供统一的仓储接口、多种持久化技术适配、事件管理、任务调度与流式事件路由能力。

## 项目简介

该项目实现了一个基于 **Facade + Delegate** 模式的仓储抽象层，作为领域层与持久化层之间的防腐层（ACL），同时集成了事件发布、任务调度与流式事件路由等基础设施能力。核心目标是：

- **解耦领域模型与持久化技术**：领域层只依赖统一的仓储接口，不关心底层使用哪种数据库
- **支持多持久化技术**：通过委托模式自动适配 MyBatis Plus、JPA、MongoDB、Elasticsearch 等
- **自动配置**：基于 Spring Boot AutoConfiguration 实现开箱即用
- **Entity-PO 自动转换**：RepositoryFacade 自动完成领域实体与持久化对象的转换
- **CQRS 读写分离**：支持一个仓储配置多个代理，写操作走基础代理，读操作走读代理，读失败自动回退
- **低代码仓储**：无需定义实体类，通过资源名称和 Map 动态操作数据，支持运行时动态注册
- **事件管理**：统一的事件发布抽象，支持 Spring 事件与消息事件两种通道
- **任务调度**：本地线程池调度与 XXL-Job 分布式调度两种实现，统一 TaskScheduler SPI
- **流式事件路由**：基于 Spring Cloud Stream 的事件监听与统一路由能力

## 模块结构

```
structure-pro-infra/
├── structure-infra-starter/                # 核心模块（仓储抽象、低代码、事件管理、调度集成）
│   ├── annotations/                        # @WriteDelegate / @ReadDelegate 注解
│   ├── configuration/                      # 自动配置（仓储、事件、调度）
│   ├── event/                              # 事件管理抽象
│   ├── lowcode/                            # 低代码仓储子系统
│   │   ├── configuration/                  # 低代码自动配置
│   │   ├── model/                          # ResourceSchema / FieldSchema 等模型
│   │   ├── properties/                     # LowCodeProperties 配置绑定
│   │   ├── registry/                       # ResourceSchemaBuilder
│   │   ├── repository/                     # LowCodeRepository / LowCodeStorage / LowCodeRepoFactory
│   │   └── router/                         # LowCodeRepositoryRouter
│   ├── properties/                         # InfraProperties 全局配置
│   └── repository/                         # 仓储核心接口（Facade / Delegate / Factory）
├── structure-infra-mybatis-plus-starter/   # MyBatis Plus 适配（含低代码 MySQL 实现）
├── structure-infra-jpa-starter/            # JPA 适配
├── structure-infra-mongodb-starter/        # MongoDB 适配（含低代码实现）
├── structure-infra-elasticsearch-starter/  # Elasticsearch 适配（含低代码实现）
├── structure-infra-schedule-starter/       # 本地任务调度（基于 ScheduledExecutorService）
├── structure-infra-xxljob-starter/         # XXL-Job 分布式任务调度适配
├── structure-infra-stream-starter/         # Spring Cloud Stream 事件路由
└── structure-infra-sample/                 # 示例模块（不发布到中央仓库）
    ├── structure-infra-sample-core/             # 共享核心（Entity、PO、Repository 接口）
    ├── structure-infra-sample-mybatis/          # MyBatis Plus 示例（含低代码测试）
    ├── structure-infra-sample-jpa/              # JPA 示例
    ├── structure-infra-sample-mongodb/          # MongoDB 示例（含 REST API、低代码测试）
    ├── structure-infra-sample-elasticsearch/    # Elasticsearch 示例（含 REST API、低代码测试）
    ├── structure-infra-sample-cqrs/             # CQRS 读写分离示例
    ├── structure-infra-sample-schedule/         # 本地调度示例（含 REST API）
    ├── structure-infra-sample-xxljob/           # XXL-Job 调度示例（含 REST API）
    └── structure-infra-sample-stream/           # 流式事件路由示例
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
- 与具体的持久化技术交互（MyBatis Plus、JPA、MongoDB、Elasticsearch 等）
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

### 事件管理

提供统一的事件发布抽象，支持三种事件通道：

| 通道 | 说明 |
|------|------|
| `DEFAULT` | 默认通道，运行时通过 `structure.infra.default-event-channel` 决定路由 |
| `SPRING_EVENT` | Spring 应用事件，通过 `ApplicationEventPublisher` 同步发布 |
| `MESSAGE_EVENT` | 消息事件，通过 `DataScopeStreamBridge` 发送到消息中间件 |

### 任务调度

提供两种调度实现，通过 `TaskScheduler` SPI 统一抽象：

| 实现 | 模块 | 适用场景 |
|------|------|---------|
| 本地线程池调度 | structure-infra-schedule-starter | 单机应用，无需外部依赖 |
| XXL-Job 分布式调度 | structure-infra-xxljob-starter | 分布式应用，需要集中管理 |

支持三种调度类型：`CRON`、`FIXED_DELAY`、`FIXED_RATE`，并提供统一的 `schedule / update / remove / pause / resume / getTaskInfo / getAllTasks` 生命周期 API。

### 流式事件路由

基于 Spring Cloud Stream 的事件监听与统一路由框架，提供：

- 动态 Binding 配置（替代静态配置）
- 基于 `eventType / businessType / condition` 的统一路由
- 注解声明式、代码动态注册、配置文件驱动、运行时动态注册四种使用方式
- SpEL 条件表达式过滤

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

**调度示例**（端口 8086）：
```bash
mvn spring-boot:run -pl structure-infra-sample/structure-infra-sample-schedule
```

**REST API 接口**（MongoDB / Elasticsearch 示例模块接口一致）：

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

**调度示例 REST API**：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/job/add` | 添加任务（CRON） |
| PUT | `/job/update/{taskId}` | 更新任务 |
| DELETE | `/job/remove/{taskId}` | 删除任务 |
| PUT | `/job/pause/{taskId}` | 暂停任务 |
| PUT | `/job/resume/{taskId}` | 恢复任务 |
| GET | `/job/info/{taskId}` | 查询任务详情 |
| GET | `/job/list` | 查询所有任务 |

详细示例模块说明请参考 [SAMPLE_MODULES.md](./SAMPLE_MODULES.md)。

### 1. 添加依赖

根据需要选择对应的 starter：

```xml
<!-- 核心模块 -->
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-starter</artifactId>
    <version>1.2.0</version>
</dependency>

<!-- MyBatis Plus 适配 -->
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-mybatis-plus-starter</artifactId>
    <version>1.2.0</version>
</dependency>

<!-- JPA 适配 -->
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-jpa-starter</artifactId>
    <version>1.2.0</version>
</dependency>

<!-- MongoDB 适配 -->
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-mongodb-starter</artifactId>
    <version>1.2.0</version>
</dependency>

<!-- Elasticsearch 适配 -->
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-elasticsearch-starter</artifactId>
    <version>1.2.0</version>
</dependency>

<!-- 本地任务调度 -->
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-schedule-starter</artifactId>
    <version>1.2.0</version>
</dependency>

<!-- XXL-Job 分布式任务调度 -->
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-xxljob-starter</artifactId>
    <version>1.2.0</version>
</dependency>

<!-- 流式事件路由 -->
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-stream-starter</artifactId>
    <version>1.2.0</version>
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

### 3. 创建 RepositoryFacade（单代理模式）

```java
@Component("userRepository")
public class UserRepositoryImpl extends RepositoryFacade<UserEntity, Long, UserMybatisPlusDelegate> 
    implements UserRepository {
}
```

### 4. 创建 Delegate 实现

```java
@Slf4j
@Component
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
```

### 5. 使用仓储

```java
@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    public UserEntity saveUser(UserEntity user) {
        return userRepository.save(user);
    }
    
    public UserEntity getUserById(Long id) {
        return userRepository.findById(id);
    }
    
    public List<UserEntity> findUsers(UserEntity condition) {
        return userRepository.queryList(condition);
    }
    
    public ResPage<UserEntity> findUsersPage(ReqPage reqPage) {
        return userRepository.queryPage(reqPage);
    }
}
```

### 6. CQRS 读写分离模式（可选）

当需要读写分离时，使用 `CqrsRepositoryFacade` 并为写代理和读代理分别添加 `@WriteDelegate` 和 `@ReadDelegate` 注解。

**1) 定义写代理**

```java
@Slf4j
@Component
@WriteDelegate
public class UserWriteDelegate 
    extends MybatisPlusRepositoryDelegate<UserEntity, UserPO, Long> 
    implements UserRepositoryDelegate {
    
    private final UserMapper userMapper;
}
```

**2) 定义读代理**

```java
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

**3) 创建 CqrsRepositoryFacade**

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

**读操作回退机制**：
- 写操作（save、removeById、saveBatch、removeBatchByIds）始终走写代理（`delegate`）
- 读操作（queryById、queryList、queryPage、count、exists 等）优先走读代理（`readDelegate`）
- 如果读代理执行失败（抛出异常），自动回退到写代理执行
- `findById` 始终走写代理（严格的实体查询）

### 7. 低代码仓储使用

低代码仓储无需定义实体类，通过资源名称和 Map 操作数据。

**1) 通过 YAML 定义资源 Schema**

```yaml
structure:
  infra:
    lowcode:
      enabled: true
      resources:
        article:
          schema:
            table-name: t_lowcode_article
            id-type: long
            fields:
              id:
                type: long
                primary-key: true
                auto-increment: true
              title:
                type: string
                length: 200
                nullable: false
              author:
                type: string
                length: 50
                index: true
              created_at:
                type: datetime
                auto-fill: create
              updated_at:
                type: datetime
                auto-fill: create_update
          repository:
            type: mysql
```

**2) 通过代码注册并使用**

```java
@Autowired
private LowCodeRepository lowCodeRepository;

// 保存数据
Map<String, Object> article = new HashMap<>();
article.put("title", "Hello World");
article.put("author", "zhangsan");
Map<String, Object> saved = lowCodeRepository.save("article", article);

// 查询数据
Map<String, Object> found = lowCodeRepository.findById("article", 1L);

// 条件查询
Map<String, Object> params = new HashMap<>();
params.put("author", "zhangsan");
List<Map<String, Object>> list = lowCodeRepository.queryList("article", params);

// 分页查询
ReqPage reqPage = new ReqPage();
reqPage.setPage(1);
reqPage.setSize(10);
ResPage<Map<String, Object>> page = lowCodeRepository.queryPage("article", reqPage);
```

**3) 切换存储引擎**

只需修改 `repository.type` 即可切换存储引擎，业务代码无需修改：

```yaml
# 使用 MySQL
repository:
  type: mysql

# 使用 MongoDB
repository:
  type: mongodb

# 使用 Elasticsearch
repository:
  type: elasticsearch
```

### 7. 任务调度使用

**1) 注册任务处理器**

```java
@Component
public class MyTaskHandlers {

    @Autowired
    private TaskHandlerRegistry handlerRegistry;

    @PostConstruct
    public void register() {
        handlerRegistry.register("myHandler", this::handleTask);
    }

    public void handleTask(String param) {
        System.out.println("任务执行，参数：" + param);
    }
}
```

**2) 调度任务**

```java
@Autowired
private TaskScheduler taskScheduler;

ScheduleTask task = ScheduleTask.builder()
        .taskId("my-task-001")
        .taskName("我的定时任务")
        .handlerName("myHandler")
        .handlerParam("hello")
        .scheduleType(ScheduleTask.ScheduleType.CRON)
        .cronExpression("0/10 * * * * ?")
        .build();

taskScheduler.schedule(task);
taskScheduler.pause("my-task-001");
taskScheduler.resume("my-task-001");
taskScheduler.remove("my-task-001");
```

### 8. 事件发布

```java
public class UserCreatedEvent implements Event {
    private final String eventId = UUID.randomUUID().toString();

    @Override
    public String getEventId() {
        return eventId;
    }
    // 默认走 DEFAULT 通道，由 structure.infra.default-event-channel 决定路由
}

@Component
@RequiredArgsConstructor
public class UserEventPublisher {
    private final EventManager eventManager;

    public void publishCreated() {
        eventManager.publish(new UserCreatedEvent());
    }
}
```

## 注解说明

### @WriteDelegate

标注在 `RepositoryDelegate` 实现类上，标记为写代理/基础代理。当存在多个同类型的 delegate bean 时，`RepositoryBeanPostProcessor` 优先选择带有此注解的 bean 作为写代理。

```java
@Qualifier
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WriteDelegate {
}
```

### @ReadDelegate

标注在 `IQueryDelegate` / `RepositoryDelegate` 实现类上，标记为读代理。`RepositoryBeanPostProcessor` 使用此注解识别读代理，用于 CQRS 模式下的读操作。

```java
@Qualifier
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ReadDelegate {
}
```

## 配置项

### 全局配置

```yaml
structure:
  infra:
    default-event-channel: SPRING_EVENT  # 默认事件通道：DEFAULT, SPRING_EVENT, MESSAGE_EVENT
    cqrs: false                           # 是否开启 CQRS（仅作建议）
    cache-time: 60                        # 默认缓存时间
    cache-time-unit: SECONDS              # 默认缓存时间单位
    schedule-pool-size: 8                 # 调度线程池大小（默认 CPU 核心数）
    type: MYBATIS_PLUS                    # 默认持久化类型
```

### 调度配置

```yaml
structure:
  schedule:
    pool-size: 4                          # 本地调度线程池大小
    xxl-job:
      enabled: true                       # 是否启用 XXL-Job
      job-group: 1                        # XXL-Job 执行器分组 ID
```

### 低代码配置

```yaml
structure:
  infra:
    lowcode:
      enabled: true                       # 是否启用低代码仓储
      resources:
        <resource-name>:
          schema:
            table-name: <table>
            id-type: long
            fields:
              <field-name>:
                type: string|long|int|boolean|decimal|datetime|date|text|json
                length: 64
                primary-key: true|false
                auto-increment: true|false
                nullable: true|false
                unique: true|false
                index: true|false
                default-value: <literal>
                auto-fill: none|create|update|create_update
          repository:
            type: mysql|mongodb|elasticsearch|redis|in_memory
            datasource: <datasource-name>
            cqrs:
              enabled: true|false
              read-type: elasticsearch
              read-datasource: es-default
            cache:
              enabled: true|false
              ttl: 300
              time-unit: seconds
```

### 流式事件配置

```yaml
structure:
  infra:
    stream:
      enabled: true                       # 是否启用
      auto-binding: true                  # 自动生成 binding 配置
      default-group: my-service           # 默认消费组
      default-concurrency: 1              # 默认并发数
      router:
        enabled: true                     # 是否启用配置驱动路由
        routes:                           # 路由列表
          - id: route-001
            event-type: orderCreated
            payload-type: com.example.OrderEvent
            handler-bean: orderHandler
            handler-method: onOrderCreated
```

## 扩展指南

### 自定义 RepositoryDelegate

当默认实现无法满足需求时，可以自定义委托实现：

```java
@Slf4j
@Component
public class UserMybatisPlusDelegate 
    extends MybatisPlusRepositoryDelegate<UserEntity, UserPO, Long> 
    implements UserRepositoryDelegate {
    
    private final UserMapper userMapper;
    
    @Override
    public UserEntity findByEmailLike(String emailPattern) {
        UserPO po = userMapper.selectOne(
            Wrappers.<UserPO>lambdaQuery().like(UserPO::getEmail, emailPattern));
        return toEntity(po);
    }
}
```

> 注意：Delegate 实现类需要通过泛型参数指定领域实体类型（T）、持久化对象类型（PO）和主键类型（ID），内部自动处理 PO 到 Entity 的转换。

### 新增持久化技术支持

1. 创建 `RepositoryDelegate` 实现类
2. 创建 `RepositoryDelegateFactory` 实现类
3. 创建 `XXXDelegateBeanPostProcessor` 用于注入依赖
4. 创建 `XXXAutoConfiguration` 自动配置类
5. 在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中注册配置类

### 新增低代码存储引擎

1. 实现 `LowCodeStorage` 接口（包含 `initialize()` 建表/集合/索引逻辑）
2. 实现 `LowCodeRepoFactory` 接口，`getType()` 返回新的 `StorageType`
3. 创建 `XXXLowCodeAutoConfiguration` 自动配置类，注册 `LowCodeRepoFactory` Bean
4. 在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中注册配置类

## 委托匹配策略

`RepositoryBeanPostProcessor` 在 Bean 初始化后执行匹配：

1. **解析泛型签名**：从 `RepositoryFacade` / `CqrsRepositoryFacade` 子类的泛型参数中提取委托类型
2. **写代理搜索**（按优先级）：
   1. 类型匹配 + `@WriteDelegate` 标记
   2. 类型匹配 + 无 `@ReadDelegate` 标记
   3. 类型匹配（兜底）
3. **读代理搜索**（仅 `CqrsRepositoryFacade`）：
   1. 类型匹配 + `@ReadDelegate` 标记
   2. 类型匹配（兜底）

### 单代理模式（RepositoryFacade）

继承自 `RepositoryFacade<T, ID, D>`，其中 `D` 是 `RepositoryDelegate<T, ID>` 的实现类。`RepositoryBeanPostProcessor` 根据泛型参数 `D` 查找对应的 delegate bean 并注入。

### CQRS 模式（CqrsRepositoryFacade）

继承自 `CqrsRepositoryFacade<T, ID, D, RD>`，其中：
- `D`：写代理类型（继承 `RepositoryDelegate<T, ID>`）
- `RD`：读代理类型（继承 `IQueryDelegate<T, ID>`）

`RepositoryBeanPostProcessor` 根据泛型参数分别查找写代理和读代理：
- 写代理：通过 `@WriteDelegate` 注解或类型匹配查找
- 读代理：通过 `@ReadDelegate` 注解或类型匹配查找

### 委托创建兜底

当未找到用户自定义的 delegate bean 时，`RepositoryDelegateFactory` 会按约定自动创建：
- 根据 PO 类名推导 Mapper 类（`xxx.po.UserPO` → `xxx.mapper.UserMapper`）
- 从 `ApplicationContext` 查找该 Mapper bean
- 找到则创建对应的 `RepositoryDelegate` 实例
- 未找到则使用默认的 `InMemoryRepositoryDelegate`（仅用于测试或无持久化场景）

## 技术栈

- Java 17+
- Spring Boot 4.0.6
- Spring Data JPA 3.3+
- MyBatis Plus 3.5.16
- Spring Data MongoDB
- Spring Data Elasticsearch
- Spring Cloud Stream 5.0.0
- XXL-Job Core

## License

Apache License 2.0
