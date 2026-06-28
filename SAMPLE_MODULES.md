# structure-pro-infra 示例模块说明

本项目提供了多种数据持久化技术的示例实现，每个存储技术都有独立的子模块。

## 模块结构

```
structure-infra-sample/
├── structure-infra-sample-core           # 核心共享模块（Entity、PO、Repository接口）
├── structure-infra-sample-mybatis        # MyBatis Plus 示例模块（含低代码测试）
├── structure-infra-sample-jpa            # JPA 示例模块
├── structure-infra-sample-mongodb        # MongoDB 示例模块（含 REST API、低代码测试）
├── structure-infra-sample-elasticsearch  # Elasticsearch 示例模块（含 REST API、低代码测试）
└── structure-infra-sample-cqrs           # CQRS 读写分离示例模块
```

## 各模块说明

### 1. 核心共享模块（structure-infra-sample-core）

**说明**：所有示例模块共享的核心代码，包含领域实体、持久化对象、仓储接口等。

**核心文件**：
- `UserEntity.java` - 用户领域实体
- `UserPO.java` - 用户持久化对象（支持多存储注解）
- `UserRepository.java` - 用户仓储接口
- `AbstractUserRepositoryImpl.java` - 用户仓储抽象实现
- `UserRepositoryDelegate.java` - 用户委托接口

---

### 2. MyBatis Plus 示例（structure-infra-sample-mybatis）

**技术栈**：
- Spring Boot 4.0.6
- MyBatis Plus 3.5.16
- H2 内存数据库（测试环境）

**功能特性**：
- 完整的 CRUD 操作实现
- 分页查询支持
- 条件查询支持
- Entity 与 PO 转换
- 低代码仓储测试（MySQL 实现）

**运行测试**：
```bash
mvn test -pl structure-infra-sample/structure-infra-sample-mybatis
```

**核心文件**：
- `InfraSampleApplication.java` - 启动类
- `UserMybatisPlusDelegate.java` - MyBatis Plus 实现
- `UserRepositoryTest.java` - 完整测试用例
- `lowcode/LowCodeTestConfig.java` - 低代码测试配置
- `lowcode/LowCodeRepositoryTest.java` - 低代码仓储测试

**状态**：✅ 已完成，测试通过

---

### 3. MongoDB 示例（structure-infra-sample-mongodb）

**技术栈**：
- Spring Boot 4.0.6
- Spring Data MongoDB
- MongoDB

**功能特性**：
- 完整的 CRUD 操作实现
- REST API 接口
- 分页查询支持
- 动态查询支持
- 生产环境配置
- 低代码仓储测试（MongoDB 实现）

**启动服务**：
```bash
mvn spring-boot:run -pl structure-infra-sample/structure-infra-sample-mongodb
# 服务端口：8081
```

**REST API 接口**：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/users` | 创建用户 |
| GET | `/api/users/{id}` | 根据ID查询用户 |
| GET | `/api/users/name/{username}` | 根据用户名查询 |
| GET | `/api/users/list` | 查询全部用户列表 |
| GET | `/api/users/page?page=1&size=10` | 分页查询 |
| PUT | `/api/users/{id}` | 更新用户 |
| DELETE | `/api/users/{id}` | 删除用户 |
| POST | `/api/users/batch` | 批量创建用户 |
| GET | `/api/users/count` | 查询用户总数 |

**请求示例**：
```bash
# 创建用户
curl -X POST http://localhost:8081/api/users \
  -H "Content-Type: application/json" \
  -d '{"username":"zhangsan","email":"zhangsan@example.com","age":25}'

# 查询用户列表
curl http://localhost:8081/api/users/list

# 分页查询
curl "http://localhost:8081/api/users/page?page=1&size=10"
```

**核心文件**：
- `MongoSampleApplication.java` - 启动类
- `MongoConfig.java` - MongoDB 配置类
- `UserController.java` - REST API 控制器
- `UserMongoRepositoryImpl.java` - 仓储实现
- `MockMongoConfiguration.java` - 测试 Mock 配置
- `UserMongoRepositoryTest.java` - 测试用例
- `lowcode/MongoLowCodeRepositoryTest.java` - 低代码仓储测试（通过 LowCodeRepository 接口）

**配置文件**：
- `application.yml` - 生产配置
- `application-mongo-test.yml` - 测试配置

**状态**：✅ 已完成，测试通过

---

### 4. Elasticsearch 示例（structure-infra-sample-elasticsearch）

**技术栈**：
- Spring Boot 4.0.6
- Spring Data Elasticsearch
- Elasticsearch 8.x

**功能特性**：
- 完整的 CRUD 操作实现
- REST API 接口
- 分页查询支持
- 全文搜索支持
- 生产环境配置
- 低代码仓储测试（Elasticsearch 实现）

**启动服务**：
```bash
mvn spring-boot:run -pl structure-infra-sample/structure-infra-sample-elasticsearch
# 服务端口：8082
```

**REST API 接口**：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/users` | 创建用户 |
| GET | `/api/users/{id}` | 根据ID查询用户 |
| GET | `/api/users/name/{username}` | 根据用户名查询 |
| GET | `/api/users/list` | 查询全部用户列表 |
| GET | `/api/users/page?page=1&size=10` | 分页查询 |
| PUT | `/api/users/{id}` | 更新用户 |
| DELETE | `/api/users/{id}` | 删除用户 |
| POST | `/api/users/batch` | 批量创建用户 |
| GET | `/api/users/count` | 查询用户总数 |

**请求示例**：
```bash
# 创建用户
curl -X POST http://localhost:8082/api/users \
  -H "Content-Type: application/json" \
  -d '{"username":"zhangsan","email":"zhangsan@example.com","age":25}'

# 查询用户列表
curl http://localhost:8082/api/users/list

# 分页查询
curl "http://localhost:8082/api/users/page?page=1&size=10"
```

**核心文件**：
- `ElasticsearchSampleApplication.java` - 启动类
- `ElasticsearchConfig.java` - Elasticsearch 配置类
- `UserController.java` - REST API 控制器
- `UserElasticsearchRepositoryImpl.java` - 仓储实现
- `MockElasticsearchConfiguration.java` - 测试 Mock 配置
- `UserElasticsearchRepositoryTest.java` - 测试用例
- `lowcode/ElasticsearchLowCodeRepositoryTest.java` - 低代码仓储测试（通过 LowCodeRepository 接口）

**配置文件**：
- `application.yml` - 生产配置（支持从配置文件读取 ES 连接信息）
- `application-es-test.yml` - 测试配置

**状态**：✅ 已完成，测试通过

---

### 5. JPA 示例（structure-infra-sample-jpa）

**技术栈**：
- Spring Data JPA
- Hibernate
- H2 内存数据库（测试环境）

**功能特性**：
- JPA Repository 实现
- Entity 管理
- 事务管理

**运行测试**：
```bash
mvn test -pl structure-infra-sample/structure-infra-sample-jpa
```

**核心文件**：
- `UserJpaRepositoryTest.java` - 测试用例
- `application-jpa-test.yml` - 测试配置

**状态**：基础实现已完成

---

### 6. CQRS 示例（structure-infra-sample-cqrs）

**技术栈**：
- MyBatis Plus（写操作）
- Elasticsearch（读操作）
- H2 内存数据库

**功能特性**：
- 读写分离模式演示
- 写操作走 MyBatis Plus
- 读操作走 Elasticsearch
- 读失败自动回退到写代理

**运行测试**：
```bash
mvn test -pl structure-infra-sample/structure-infra-sample-cqrs
```

**核心文件**：
- `CqrsApplication.java` - 启动类
- `UserCqrsRepositoryTest.java` - CQRS 测试用例

**状态**：基础实现已完成

---

## 低代码仓储测试

低代码仓储是一套无需定义实体类的动态数据访问方案，通过 `LowCodeRepository` 接口统一操作不同存储引擎。

### 测试覆盖

所有低代码测试均通过 `LowCodeRepository` 接口进行，验证完整的路由机制：

| 方法 | 说明 | MySQL | MongoDB | Elasticsearch |
|------|------|:-----:|:-------:|:-------------:|
| `save` | 新增和更新 | ✅ | ✅ | ✅ |
| `findById` | 根据 ID 查询 | ✅ | ✅ | ✅ |
| `queryById` | 根据 ID 查询（读操作） | ✅ | ✅ | ✅ |
| `queryByIdOptional` | 根据 ID 查询（Optional） | ✅ | ✅ | ✅ |
| `queryOne` | 条件查询单条 | ✅ | ✅ | ✅ |
| `queryOneOptional` | 条件查询单条（Optional） | ✅ | ✅ | ✅ |
| `queryList` | 条件查询列表 | ✅ | ✅ | ✅ |
| `queryPage` | 分页查询 | ✅ | ✅ | ✅ |
| `removeById` | 根据 ID 删除 | ✅ | ✅ | ✅ |
| `saveBatch` | 批量保存 | ✅ | ✅ | ✅ |
| `removeBatchByIds` | 批量删除 | ✅ | ✅ | ✅ |
| `listByIds` | 批量查询 | ✅ | ✅ | ✅ |
| `count` | 统计数量 | ✅ | ✅ | ✅ |
| `exists` | 判断存在 | ✅ | ✅ | ✅ |
| `testAutoFill` | 自动填充时间字段 | ✅ | ✅ | ✅ |

### 运行低代码测试

```bash
# MySQL 低代码测试
mvn test -pl structure-infra-sample/structure-infra-sample-mybatis \
  -Dtest="cn.structure.infra.sample.lowcode.LowCodeRepositoryTest"

# MongoDB 低代码测试
mvn test -pl structure-infra-sample/structure-infra-sample-mongodb \
  -Dtest="cn.structure.infra.sample.mongodb.lowcode.MongoLowCodeRepositoryTest"

# Elasticsearch 低代码测试
mvn test -pl structure-infra-sample/structure-infra-sample-elasticsearch \
  -Dtest="cn.structure.infra.sample.elasticsearch.lowcode.ElasticsearchLowCodeRepositoryTest"
```

### 测试策略

低代码测试采用纯单元测试方式，使用 Mockito 直接模拟底层存储模板：

- **MySQL**：模拟 MyBatis Plus 的 Mapper，使用内存 Map 存储数据
- **MongoDB**：模拟 MongoTemplate，使用内存 Map 模拟集合，支持 Query 条件解析
- **Elasticsearch**：模拟 ElasticsearchOperations，使用内存 Map 模拟索引，支持 Criteria 条件解析

---

## 测试说明

### Mock 测试配置

MongoDB 和 Elasticsearch 示例模块提供了 Mock 配置，用于在没有真实数据库服务的情况下运行测试：

**MongoDB Mock**：
- `MockMongoConfiguration.java` - 用 HashMap 模拟 MongoDB 存储
- 支持 save、findById、find、remove、count 等操作
- 支持 Query 条件过滤

**Elasticsearch Mock**：
- `MockElasticsearchConfiguration.java` - 用 HashMap 模拟 ES 存储
- 支持 save、get、search、delete、count 等操作
- 支持分页查询

### 运行所有测试

```bash
# MyBatis Plus
mvn test -pl structure-infra-sample/structure-infra-sample-mybatis

# MongoDB（使用 Mock）
mvn test -pl structure-infra-sample/structure-infra-sample-mongodb

# Elasticsearch（使用 Mock）
mvn test -pl structure-infra-sample/structure-infra-sample-elasticsearch

# JPA
mvn test -pl structure-infra-sample/structure-infra-sample-jpa

# CQRS
mvn test -pl structure-infra-sample/structure-infra-sample-cqrs
```

---

## 配置说明

### MongoDB 配置（application.yml）

```yaml
server:
  port: 8081

structure:
  infra:
    type: MONGODB

spring:
  data:
    mongodb:
      uri: mongodb://user:password@host:27017/database?authSource=admin
```

### Elasticsearch 配置（application.yml）

```yaml
server:
  port: 8082

structure:
  infra:
    type: ELASTICSEARCH

spring:
  elasticsearch:
    uris: http://host:9200
    username: elastic
    password: your_password
```

---

## Delegate 模式说明

每个存储技术都通过实现 `UserRepositoryDelegate` 接口来提供数据访问能力：

```java
@Repository(
    value = "用户仓储",
    type = RepositoryType.MONGODB,  // 指定存储类型
    entity = UserEntity.class,
    po = UserPO.class
)
@Component("userRepository")
public class UserMongoRepositoryImpl extends AbstractUserRepositoryImpl {
    // 继承基类，自动获得 CRUD 能力
}
```

### RepositoryType 枚举

| 类型 | 说明 |
|------|------|
| `MYBATIS_PLUS` | MyBatis Plus 实现 |
| `JPA` | JPA 实现 |
| `MONGODB` | MongoDB 实现 |
| `ELASTICSEARCH` | Elasticsearch 实现 |
| `AUTO` | 自动选择 |

---

## 依赖关系

```
structure-infra-sample-core（共享模块）
    ├── structure-infra-starter
    └── structure-common

structure-infra-sample-mybatis
    ├── structure-infra-sample-core
    ├── structure-infra-mybatis-plus-starter
    └── structure-common

structure-infra-sample-mongodb
    ├── structure-infra-sample-core
    ├── structure-infra-mongodb-starter
    ├── spring-boot-starter-web
    └── structure-common

structure-infra-sample-elasticsearch
    ├── structure-infra-sample-core
    ├── structure-infra-elasticsearch-starter
    ├── spring-boot-starter-web
    └── structure-common

structure-infra-sample-jpa
    ├── structure-infra-sample-core
    ├── structure-infra-jpa-starter
    └── structure-common

structure-infra-sample-cqrs
    ├── structure-infra-sample-core
    ├── structure-infra-mybatis-plus-starter
    ├── structure-infra-elasticsearch-starter
    └── structure-common
```

---

## 注意事项

1. **Mock 测试**：MongoDB 和 Elasticsearch 模块提供了 Mock 配置，测试无需真实数据库服务
2. **生产启动**：启动 MongoDB/ES 示例服务需要相应的数据库服务运行
3. **配置读取**：ES 配置类会自动从 application.yml 读取连接信息
4. **依赖隔离**：每个示例模块都排除了其他存储技术的依赖，避免冲突
5. **代码复用**：所有模块共享 core 模块中的 Entity、PO、Repository 接口
6. **低代码测试**：低代码测试使用纯单元测试方式，不依赖 Spring 上下文，执行速度更快

---

## 许可证

本项目遵循 Apache License 2.0
