# structure-pro-infra

基于 DDD（领域驱动设计）理念的基础设施抽象层，提供统一的仓储接口和多种持久化技术的适配实现。

## 项目简介

该项目实现了一个基于 **Facade + Delegate** 模式的仓储抽象层，作为领域层与持久化层之间的防腐层（ACL），核心目标是：

- **解耦领域模型与持久化技术**：领域层只依赖统一的仓储接口，不关心底层使用哪种数据库
- **支持多持久化技术**：通过委托模式自动适配 MyBatis Plus、JPA、MongoDB、Elasticsearch 等
- **自动配置**：基于 Spring Boot AutoConfiguration 实现开箱即用
- **Entity-PO 自动转换**：RepositoryFacade 自动完成领域实体与持久化对象的转换
- **CQRS 读写分离**：支持一个仓储配置多个代理，写操作走基础代理，读操作走读代理

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
│   └── event/                         # 事件管理
├── structure-infra-mybatis-plus-starter/ # MyBatis Plus 适配
├── structure-infra-jpa-starter/        # JPA 适配
├── structure-infra-mongodb-starter/    # MongoDB 适配
├── structure-infra-elasticsearch-starter/ # Elasticsearch 适配
└── structure-infra-sample/             # 示例模块（已注释）
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

## 快速开始

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