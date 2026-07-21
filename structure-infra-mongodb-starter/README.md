# Structure Infra MongoDB Starter

基于 Spring Data MongoDB 的仓储适配模块，为 `structure-infra-starter` 提供 MongoDB 类型的 `RepositoryDelegate` 与低代码 `LowCodeStorage` 实现，使领域仓储能够透明地操作 MongoDB 文档数据库。

## 功能特性

- **自动配置**：检测到 `MongoTemplate` 时自动启用，注册委托工厂与 Bean 后处理器
- **类型化仓储**：通过 `MongoRepositoryDelegate<T, ID>` 实现完整的 CRUD/分页/批量操作
- **委托自动创建**：未提供自定义 Delegate 时，由 `MongoDelegateFactory` 根据PO类自动创建
- **自定义 Delegate 自动注入**：通过 `MongoDelegateBeanPostProcessor` 自动注入 `MongoTemplate` 与实体类
- **低代码仓储**：通过 `MongoLowCodeStorage` 使用 `Document` 动态操作集合，无需定义实体类
- **自动建集合与索引**：低代码初始化时自动创建集合、主键索引、唯一索引、普通索引
- **自动填充**：支持 `CREATE_TIME` / `UPDATE_TIME` / `CREATE_UPDATE` 自动填充
- **条件查询**：根据非空字段动态构建 `Criteria` 等值查询
- **CQRS 支持**：可作为 BASE 或 READ 代理参与读写分离

## 添加依赖

```xml
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-mongodb-starter</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

依赖中已包含 `spring-boot-starter-data-mongodb`，无需重复引入。

## 配置说明

### 基础配置

```yaml
structure:
  infra:
    type: MONGODB              # 显式指定存储类型（matchIfMissing=true 时可不配）
    lowcode:
      enabled: true            # 启用低代码（默认开启）

spring:
  data:
    mongodb:
      uri: mongodb://user:password@host:27017/database?authSource=admin
      # 或拆分配置：
      # host: localhost
      # port: 27017
      # database: mydb
      # username: user
      # password: password
```

### 自动配置触发条件

| 条件 | 说明 |
|------|------|
| `@ConditionalOnClass(MongoTemplate)` | 类路径存在 Spring Data MongoDB |
| `@ConditionalOnProperty(structure.infra.type=MONGODB, matchIfMissing=true)` | 显式指定或默认启用 |
| `@ConditionalOnBean(MongoTemplate.class)` | Spring 上下文中存在 `MongoTemplate` Bean |
| `@ConditionalOnProperty(structure.infra.lowcode.enabled=true, matchIfMissing=true)` | 低代码默认启用 |

注册的 AutoConfiguration（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）：

- `cn.structure.infra.mongodb.configuration.MongoAutoConfiguration`
- `cn.structure.infra.mongodb.lowcode.MongoLowCodeAutoConfiguration`

## 核心组件

### 1. 类型化仓储

#### MongoRepositoryDelegate<T, ID>

实现 `RepositoryDelegate<T, ID>` 接口，基于 `MongoTemplate` 完成文档操作。

| 方法 | 说明 |
|------|------|
| `save(T)` | 保存（调用 `mongoTemplate.save`） |
| `removeById(ID)` | 根据 ID 删除 |
| `findById(ID)` | 根据 ID 查询 |
| `queryById(ID)` / `queryByIdOptional(ID)` | 读代理查询，默认走 `findById` |
| `queryOne(T)` / `queryOneOptional(T)` | 根据非空字段构建 `Criteria` 等值查询单条 |
| `queryList(T)` | 条件为空时 `findAll`，否则条件查询列表 |
| `queryPage(ReqPage)` | 分页查询，`PageRequest.of(page-1, size, Sort.unsorted())` |
| `saveBatch(List<T>)` | 逐条 `save`，返回保存后的列表 |
| `removeBatchByIds(List<ID>)` | 根据 ID 列表批量删除（`Criteria.in`） |
| `listByIds(List<ID>)` | 根据 ID 列表批量查询 |
| `count(T)` | 条件统计数量 |
| `exists(T)` | 条件判断是否存在 |

**条件查询构建规则**：通过反射遍历对象所有字段（含父类），非空字段拼装为 `Criteria.where(name).is(value)` 等值条件。

**ID 字段**：默认 `id`，可通过构造器或 `setIdFieldName` 修改。

#### MongoDelegateFactory

实现 `RepositoryDelegateFactory` SPI：

- `getType()` 返回 `RepositoryType.MONGODB`
- `createDelegate(poClass, idClass)` 创建 `MongoRepositoryDelegate(mongoTemplate, poClass)`

当 `RepositoryFacade` 未找到用户自定义的 MongoDB 类型 Delegate 时，由 `RepositoryBeanPostProcessor` 调用此工厂自动创建。

#### MongoDelegateBeanPostProcessor

实现 `BeanPostProcessor`，在 Bean 初始化后处理自定义的 `MongoRepositoryDelegate` 实现：

1. 检测 Bean 是否为 `MongoRepositoryDelegate` 实例
2. 从 Spring 上下文获取 `MongoTemplate` 并注入
3. 通过泛型解析设置 `entityClass`

### 2. 低代码仓储

#### MongoLowCodeAutoConfiguration

低代码自动配置类，注册 `MongoLowCodeRepoFactory` Bean。

#### MongoLowCodeRepoFactory

实现 `LowCodeRepoFactory` SPI：

- `getType()` 返回 `StorageType.MONGODB`
- `createStorage(schema, config)` 创建 `MongoLowCodeStorage(schema, mongoTemplate)`

被 `LowCodeRepositoryRouter` 根据 `StorageType` 路由调用。

#### MongoLowCodeStorage

实现 `LowCodeStorage` 接口，使用 `Document` 代替实体类操作 MongoDB 集合。

**初始化（initialize）**：
- 检查集合是否存在，不存在则 `createCollection`
- 遍历 `FieldSchema`：主键字段、`index=true`、`unique=true` 字段自动创建索引
- `unique=true` 字段创建唯一索引

**CRUD 操作**：

| 方法 | 实现说明 |
|------|---------|
| `save(Map)` | 有 ID 且存在 → `updateFirst`；否则 `insert`。自动填充 CREATE/CREATE_UPDATE 字段 |
| `findById(Object)` | `Criteria.where(idField).is(id)` 查询 |
| `queryOne(Map)` / `queryList(Map)` | 根据 schema 中已定义字段构建等值 `Criteria` 查询 |
| `queryPage(ReqPage)` | 先 `count` 总数，再分页 `find`，返回 `ResPage` |
| `removeById(Object)` | 根据 ID 删除单条 |
| `saveBatch(List<Map>)` | 逐条调用 `save` |
| `removeBatchByIds(List)` | `Criteria.where(idField).in(ids)` 批量删除 |
| `listByIds(List)` | 根据 ID 列表批量查询 |
| `count(Map)` / `exists(Map)` | 条件统计 |

**自动填充**：根据 `FieldSchema.autoFill` 类型，在 `save` 时填充：
- `CREATE_TIME` / `CREATE_UPDATE`：插入时填充 `LocalDateTime` 或 `LocalDate`
- `UPDATE_TIME`：当前实现仅在 CREATE/CREATE_UPDATE 时填充，更新时由 `doUpdate` 写入字段值

**Document ↔ Map 转换**：所有返回值统一转换为 `Map<String, Object>`，对调用方屏蔽 BSON 类型。

## 使用示例

### 方式一：类型化仓储（推荐用于领域模型）

```java
// 1. PO 类（无需 @Document，由 RepositoryFacade 通过 entityClass 操作）
public class UserPO {
    private String id;
    private String username;
    private String email;
    private Integer age;
    // getter/setter
}

// 2. 仓储接口
public interface UserRepository extends ICrudRepository<UserEntity, String> {}

// 3. 仓储实现，继承 RepositoryFacade
@Component("userRepository")
public class UserRepositoryImpl
        extends RepositoryFacade<UserEntity, String, UserRepositoryDelegate>
        implements UserRepository {

    // 未提供自定义 Delegate 时，框架会通过 MongoDelegateFactory 自动创建
}
```

### 方式二：自定义 Delegate

```java
@Component
public class UserMongoRepositoryDelegate 
        extends MongoRepositoryDelegate<UserEntity, UserPO, String>
        implements UserRepositoryDelegate {

    // 可覆写 queryOne/queryList 等方法实现自定义查询逻辑
    // MongoDelegateBeanPostProcessor 会自动注入 MongoTemplate 和 entityClass
}
```

### 方式三：低代码仓储（无需定义 PO）

```java
// 通过 LowCodeRepository 接口操作
@Service
public class DynamicDataService {
    private final LowCodeRepository lowCodeRepository;

    public void saveUser(Map<String, Object> data) {
        // schemaName 对应 LowCodeProperties.resources 中定义的资源名
        lowCodeRepository.save("user", data);
    }

    public Map<String, Object> findById(String id) {
        return lowCodeRepository.findById("user", id);
    }

    public ResPage<Map<String, Object>> queryPage(ReqPage reqPage) {
        return lowCodeRepository.queryPage("user", reqPage);
    }
}
```

### 低代码资源配置

```yaml
structure:
  infra:
    lowcode:
      enabled: true
      resources:
        - name: user
          table-name: t_user
          storage-type: MONGODB
          fields:
            - name: id
              type: STRING
              primary-key: true
            - name: username
              type: STRING
              index: true
            - name: email
              type: STRING
              unique: true
            - name: created_at
              type: DATETIME
              auto-fill: CREATE_TIME
            - name: updated_at
              type: DATETIME
              auto-fill: CREATE_UPDATE
```

## 字段类型映射

| FieldType | Java 类型 | MongoDB 存储 |
|-----------|----------|--------------|
| STRING | String | string |
| INTEGER | Integer | int32 |
| LONG | Long | int64 |
| DECIMAL | BigDecimal | decimal |
| BOOLEAN | Boolean | bool |
| DATE | LocalDate | date |
| DATETIME | LocalDateTime | date |
| TEXT | String | string |

## 注意事项

1. **集合创建**：低代码初始化时若集合不存在会自动创建，已存在则跳过
2. **索引创建**：每次初始化都会 `ensureIndex`，MongoDB 对已存在的索引会忽略
3. **条件查询**：当前仅支持等值查询（`Criteria.is`），暂不支持范围、模糊等复杂条件
4. **分页排序**：默认使用 `Sort.unsorted()`，暂未支持通过 `ReqPage` 传递排序字段
5. **批量保存**：`saveBatch` 通过循环单条 `save` 实现，未使用 `bulkOps`，大批量场景需评估性能
6. **事务**：MongoDB 4.0+ 支持多文档事务，需在 `MongoTemplate` 配置 `MongoTransactionManager`
7. **ID 字段**：默认 `id`，若 PO 使用其他主键字段名需通过构造器或 setter 指定
8. **PO 复用**：与 MyBatis Plus / JPA / Elasticsearch 共享同一 PO 时，需注意多存储注解兼容性

## 许可证

本项目遵循 Apache License 2.0
