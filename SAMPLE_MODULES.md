# structure-pro-infra 示例模块说明

本项目提供了多种数据持久化技术的示例实现，每个存储技术都有独立的子模块。

## 模块结构

```
structure-pro-infra
├── structure-infra-sample                    # MyBatis Plus 示例（主模块）
├── structure-infra-sample-jpa              # JPA 示例模块
├── structure-infra-sample-mongodb         # MongoDB 示例模块
└── structure-infra-sample-elasticsearch    # Elasticsearch 示例模块
```

## 各模块说明

### 1. MyBatis Plus 示例（structure-infra-sample）

**技术栈**：
- Spring Boot 4.0.6
- MyBatis Plus 3.5.16
- H2 内存数据库（测试环境）

**功能特性**：
- 完整的 CRUD 操作实现
- 分页查询支持
- 条件查询支持
- Entity 与 PO 转换

**运行测试**：
```bash
mvn test -pl structure-infra-sample -Dtest=UserRepositoryTest
```

**核心文件**：
- `UserMybatisPlusDelegate.java` - MyBatis Plus 实现
- `UserRepositoryImpl.java` - 仓储入口
- `UserRepositoryTest.java` - 完整测试用例

---

### 2. JPA 示例（structure-infra-sample-jpa）

**技术栈**：
- Spring Data JPA
- Hibernate
- H2 内存数据库（测试环境）

**功能特性**：
- JPA Repository 实现（待完善）
- Entity 管理
- 事务管理

**运行测试**：
```bash
mvn test -pl structure-infra-sample-jpa -Dtest=UserJpaRepositoryTest
```

**核心文件**：
- `UserJpaDelegate.java` - JPA 实现
- `JpaTestConfig.java` - 测试配置
- `application-jpa-test.yml` - 测试配置

**状态**：待实现完整功能

---

### 3. MongoDB 示例（structure-infra-sample-mongodb）

**技术栈**：
- Spring Data MongoDB
- MongoDB

**功能特性**：
- MongoDB Repository 实现（待完善）
- 文档存储支持
- 动态查询

**运行测试**：
```bash
mvn test -pl structure-infra-sample-mongodb -Dtest=UserMongoRepositoryTest
```

**核心文件**：
- `UserMongoDelegate.java` - MongoDB 实现
- `MongoTestConfig.java` - 测试配置
- `application-mongo-test.yml` - 测试配置

**状态**：待实现完整功能

---

### 4. Elasticsearch 示例（structure-infra-sample-elasticsearch）

**技术栈**：
- Spring Data Elasticsearch
- Elasticsearch

**功能特性**：
- Elasticsearch Repository 实现（待完善）
- 全文搜索支持
- 复杂查询支持

**运行测试**：
```bash
mvn test -pl structure-infra-sample-elasticsearch -Dtest=UserElasticsearchRepositoryTest
```

**核心文件**：
- `UserEsDelegate.java` - Elasticsearch 实现
- `ElasticsearchTestConfig.java` - 测试配置
- `application-es-test.yml` - 测试配置

**状态**：待实现完整功能

---

## Delegate 模式说明

每个存储技术都通过实现 `UserRepositoryDelegate` 接口来提供数据访问能力：

```java
@DelegateFor(
    name = "userRepository",
    type = RepositoryType.MYBATIS_PLUS,  // 指定存储类型
    po = UserPO.class,                     // 持久化对象类型
    description = "用户仓储 MyBatis Plus 实现",
    priority = 10                          // 优先级
)
public class UserMybatisPlusDelegate implements UserRepositoryDelegate {
    // 实现所有数据访问方法
}
```

### RepositoryType 枚举

- `MYBATIS_PLUS` - MyBatis Plus 实现
- `JPA` - JPA 实现
- `MONGODB` - MongoDB 实现
- `ELASTICSEARCH` - Elasticsearch 实现
- `AUTO` - 自动选择

### 委托选择机制

框架会根据 `@Repository` 注解的 `type` 属性自动选择对应的 delegate 实现：

```java
@Repository(
    value = "用户仓储",
    type = RepositoryType.MYBATIS_PLUS,  // 指定使用 MyBatis Plus
    entity = UserEntity.class,
    po = UserPO.class
)
@Component("userRepository")
public class UserRepositoryImpl extends RepositoryFacade<...> implements UserRepository {
    // 框架会自动注入 type=MYBATIS_PLUS 的 delegate
}
```

---

## 统一测试

运行所有示例模块的测试：

```bash
# MyBatis Plus（已完整实现）
mvn test -pl structure-infra-sample

# JPA（待完善）
mvn test -pl structure-infra-sample-jpa

# MongoDB（待完善）
mvn test -pl structure-infra-sample-mongodb

# Elasticsearch（待完善）
mvn test -pl structure-infra-sample-elasticsearch
```

---

## 开发指南

### 添加新的存储技术实现

1. **创建新的子模块**：
```bash
mkdir -p structure-infra-sample-{tech}/src/main/java/...
```

2. **创建 Delegate 实现**：
```java
@Component
@DelegateFor(
    name = "userRepository",
    type = RepositoryType.{TECH},
    po = UserPO.class,
    description = "用户仓储 {TECH} 实现",
    priority = 10
)
public class User{Tech}Delegate implements UserRepositoryDelegate {
    // 实现所有方法
}
```

3. **创建测试配置**：
```java
@Configuration
@Enable{Tech}Repositories(...)
public class {Tech}TestConfig {
}
```

4. **更新父 pom.xml**：
```xml
<modules>
    <module>structure-infra-sample-{tech}</module>
</modules>
```

---

## 依赖关系

```
structure-infra-sample (MyBatis Plus)
    ├── structure-infra-starter
    ├── structure-infra-mybatis-plus-starter
    └── structure-common

structure-infra-sample-jpa (JPA)
    ├── structure-infra-sample
    ├── structure-infra-jpa-starter
    └── structure-common

structure-infra-sample-mongodb (MongoDB)
    ├── structure-infra-sample
    ├── structure-infra-mongodb-starter
    └── structure-common

structure-infra-sample-elasticsearch (Elasticsearch)
    ├── structure-infra-sample
    ├── structure-infra-elasticsearch-starter
    └── structure-common
```

---

## 注意事项

1. **测试环境**：JPA、MongoDB、Elasticsearch 模块的完整测试需要相应的数据库服务运行
2. **依赖隔离**：每个示例模块都排除了其他存储技术的依赖，避免冲突
3. **代码复用**：所有模块共享 `structure-infra-sample` 中的 Entity、PO、Repository 接口等核心代码
4. **持续完善**：JPA、MongoDB、Elasticsearch 的具体实现需要根据业务需求补充

---

## 许可证

本项目遵循 Apache License 2.0
