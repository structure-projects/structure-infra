# Structure Infra Elasticsearch Starter

基于 Spring Data Elasticsearch 的仓储适配模块，为 `structure-infra-starter` 提供 Elasticsearch 类型的 `RepositoryDelegate` 与低代码 `LowCodeStorage` 实现，使领域仓储能够透明地操作 Elasticsearch 搜索引擎。

## 功能特性

- **自动配置**：检测到 `ElasticsearchOperations` 时自动启用，注册委托工厂与 Bean 后处理器
- **类型化仓储**：通过 `ElasticsearchRepositoryDelegate<T, ID>` 实现完整的 CRUD/分页/批量操作
- **委托自动创建**：未提供自定义 Delegate 时，由 `ElasticsearchDelegateFactory` 根据PO类自动创建
- **自定义 Delegate 自动注入**：通过 `ElasticsearchDelegateBeanPostProcessor` 自动注入 `ElasticsearchOperations` 与实体类
- **低代码仓储**：通过 `ElasticsearchLowCodeStorage` 使用 `Map` 动态操作文档，无需定义实体类
- **自动建索引**：低代码初始化时自动创建 Elasticsearch 索引
- **自动填充**：支持 `CREATE_TIME` / `CREATE_UPDATE` 自动填充
- **条件查询**：根据非空字段动态构建 `Criteria` 等值查询
- **CQRS 支持**：可作为 BASE 或 READ 代理参与读写分离，常作为读侧高速检索引擎

## 添加依赖

```xml
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-elasticsearch-starter</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

依赖中已包含 `spring-boot-starter-data-elasticsearch`，无需重复引入。

## 配置说明

### 基础配置

```yaml
structure:
  infra:
    type: ELASTICSEARCH         # 显式指定存储类型（matchIfMissing=true 时可不配）
    lowcode:
      enabled: true             # 启用低代码（默认开启）

spring:
  elasticsearch:
    uris: http://localhost:9200
    username: elastic           # 可选
    password: your_password     # 可选
    connection-timeout: 1000    # 连接超时（毫秒）
    socket-timeout: 30000       # Socket 超时（毫秒）
```

### 自动配置触发条件

| 条件 | 说明 |
|------|------|
| `@ConditionalOnClass(ElasticsearchOperations)` | 类路径存在 Spring Data Elasticsearch |
| `@ConditionalOnProperty(structure.infra.type=ELASTICSEARCH, matchIfMissing=true)` | 显式指定或默认启用 |
| `@ConditionalOnBean(ElasticsearchOperations.class)` | Spring 上下文中存在 `ElasticsearchOperations` Bean |
| `@ConditionalOnProperty(structure.infra.lowcode.enabled=true, matchIfMissing=true)` | 低代码默认启用 |

注册的 AutoConfiguration（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）：

- `cn.structure.infra.elasticsearch.configuration.ElasticsearchAutoConfiguration`
- `cn.structure.infra.elasticsearch.lowcode.ElasticsearchLowCodeAutoConfiguration`

## 核心组件

### 1. 类型化仓储

#### ElasticsearchRepositoryDelegate<T, ID>

实现 `RepositoryDelegate<T, ID>` 接口，基于 `ElasticsearchOperations` 完成文档操作。

| 方法 | 说明 |
|------|------|
| `save(T)` | 保存（调用 `elasticsearchOperations.save`） |
| `removeById(ID)` | 根据 ID 删除（`String.valueOf(id)`） |
| `findById(ID)` | 根据 ID 查询（`elasticsearchOperations.get`） |
| `queryById(ID)` / `queryByIdOptional(ID)` | 读代理查询，默认走 `findById` |
| `queryOne(T)` / `queryOneOptional(T)` | 根据非空字段构建 `Criteria` 等值查询单条，取首条 |
| `queryList(T)` | 条件为空时 `Criteria.where("*").exists()` 全查，否则条件查询列表 |
| `queryPage(ReqPage)` | 分页查询，`PageRequest.of(page-1, size, Sort.unsorted())`，从 `SearchHits.getTotalHits()` 取总数 |
| `saveBatch(List<T>)` | 逐条 `save`，返回保存后的列表 |
| `removeBatchByIds(List<ID>)` | 循环 `delete` 逐条删除 |
| `listByIds(List<ID>)` | 循环 `findById` 逐条查询，过滤 null |
| `count(T)` | 条件统计数量 |
| `exists(T)` | 条件判断是否存在 |

**条件查询构建规则**：通过反射遍历对象所有字段（含父类），非空字段拼装为 `Criteria.where(name).is(value)` 链式 `and` 查询。

**ID 字段**：默认 `id`，可通过构造器或 `setIdFieldName` 修改。ES 操作时统一转换为 `String` 作为文档 ID。

#### ElasticsearchDelegateFactory

实现 `RepositoryDelegateFactory` SPI：

- `getType()` 返回 `RepositoryType.ELASTICSEARCH`
- `createDelegate(poClass, idClass)` 创建 `ElasticsearchRepositoryDelegate(elasticsearchOperations, poClass)`

当 `RepositoryFacade` 未找到用户自定义的 Elasticsearch 类型 Delegate 时，由 `RepositoryBeanPostProcessor` 调用此工厂自动创建。

#### ElasticsearchDelegateBeanPostProcessor

实现 `BeanPostProcessor`，在 Bean 初始化后处理自定义的 `ElasticsearchRepositoryDelegate` 实现：

1. 检测 Bean 是否为 `ElasticsearchRepositoryDelegate` 实例
2. 从 Spring 上下文获取 `ElasticsearchOperations` 并注入
3. 通过泛型解析设置 `entityClass`

### 2. 低代码仓储

#### ElasticsearchLowCodeAutoConfiguration

低代码自动配置类，注册 `ElasticsearchLowCodeRepoFactory` Bean。

#### ElasticsearchLowCodeRepoFactory

实现 `LowCodeRepoFactory` SPI：

- `getType()` 返回 `StorageType.ELASTICSEARCH`
- `createStorage(schema, config)` 创建 `ElasticsearchLowCodeStorage(schema, elasticsearchOperations)`

被 `LowCodeRepositoryRouter` 根据 `StorageType` 路由调用。

#### ElasticsearchLowCodeStorage

实现 `LowCodeStorage` 接口，使用 `Map<String, Object>` 代替实体类操作 ES 索引，使用 `IndexCoordinates.of(tableName)` 定位索引。

**初始化（initialize）**：
- 检查索引是否存在，不存在则 `indexOps.create()`
- 不创建 mapping，使用 ES 动态映射

**CRUD 操作**：

| 方法 | 实现说明 |
|------|---------|
| `save(Map)` | 自动填充字段；有 ID 且存在 → `doUpdate`（先 delete 再 index）；否则 `doIndex` |
| `doIndex(Map)` | `IndexQueryBuilder.withId(id).withObject(data)` 构建 `IndexQuery` 执行索引 |
| `doUpdate(Map)` | 先 `delete` 旧文档，再 `doIndex` 重新索引（非部分更新） |
| `findById(Object)` | `elasticsearchOperations.get(id, Map.class, indexCoordinates)` |
| `queryOne(Map)` / `queryList(Map)` | 根据 schema 中已定义字段构建等值 `Criteria` 查询，返回 `Map` |
| `queryPage(ReqPage)` | `PageRequest` 分页查询，从 `SearchHits.getTotalHits()` 取总数 |
| `removeById(Object)` | `elasticsearchOperations.delete(id, indexCoordinates)` |
| `saveBatch(List<Map>)` | 逐条调用 `save` |
| `removeBatchByIds(List)` | 循环 `delete` 逐条删除 |
| `listByIds(List)` | 循环 `findById` 逐条查询，过滤 null |
| `count(Map)` / `exists(Map)` | 条件统计 |

**自动填充**：根据 `FieldSchema.autoFill` 类型，在 `save` 时填充：
- `CREATE_TIME` / `CREATE_UPDATE`：插入时填充 `LocalDateTime` 或 `LocalDate`
- `UPDATE_TIME`：当前实现仅在 CREATE/CREATE_UPDATE 时填充

**全量查询条件**：当 `queryParams` 为空时，使用 `Criteria.where("_id").exists()` 匹配所有文档。

## 使用示例

### 方式一：类型化仓储（推荐用于领域模型）

```java
// 1. PO 类（@Document 指定索引名）
@Document(indexName = "user")
public class UserPO {
    @Id
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

    // 未提供自定义 Delegate 时，框架会通过 ElasticsearchDelegateFactory 自动创建
}
```

### 方式二：自定义 Delegate

```java
@Component
public class UserElasticsearchRepositoryDelegate
        extends ElasticsearchRepositoryDelegate<UserEntity, UserPO, String>
        implements UserRepositoryDelegate {

    // 可覆写 queryOne/queryList 等方法实现自定义查询逻辑
    // ElasticsearchDelegateBeanPostProcessor 会自动注入 ElasticsearchOperations 和 entityClass
}
```

### 方式三：低代码仓储（无需定义 PO）

```java
@Service
public class SearchService {
    private final LowCodeRepository lowCodeRepository;

    public void indexUser(Map<String, Object> data) {
        lowCodeRepository.save("user", data);
    }

    public Map<String, Object> findById(String id) {
        return lowCodeRepository.findById("user", id);
    }

    public ResPage<Map<String, Object>> searchPage(ReqPage reqPage) {
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
          table-name: user           # 对应 ES 索引名
          storage-type: ELASTICSEARCH
          fields:
            - name: id
              type: STRING
              primary-key: true
            - name: username
              type: STRING
              index: true
            - name: email
              type: STRING
            - name: age
              type: INTEGER
            - name: created_at
              type: DATETIME
              auto-fill: CREATE_TIME
            - name: updated_at
              type: DATETIME
              auto-fill: CREATE_UPDATE
```

## CQRS 读写分离

Elasticsearch 常作为读侧高速检索引擎，与 MyBatis Plus / MongoDB 组合实现 CQRS：

```java
// 写代理（MyBatis Plus）
@Component
@WriteDelegate
public class UserWriteDelegate
        extends MybatisPlusRepositoryDelegate<UserEntity, UserPO, String>
        implements UserRepositoryDelegate {
}

// 读代理（Elasticsearch）
@Component
@ReadDelegate
public class UserReadDelegate
        extends ElasticsearchRepositoryDelegate<UserEntity, UserPO, String>
        implements UserRepositoryDelegate {
}

// CQRS 仓储
@Component("userCqrsRepository")
public class UserCqrsRepository
        extends CqrsRepositoryFacade<UserEntity, String, UserWriteDelegate, UserReadDelegate>
        implements UserRepository {
    // 写操作 → UserWriteDelegate（MyBatis Plus）
    // 读操作 → UserReadDelegate（Elasticsearch）
    // 读失败自动回退到 UserWriteDelegate
}
```

## 字段类型映射

| FieldType | Java 类型 | Elasticsearch 类型 |
|-----------|----------|-------------------|
| STRING | String | keyword / text |
| INTEGER | Integer | integer |
| LONG | Long | long |
| DECIMAL | BigDecimal | double |
| BOOLEAN | Boolean | boolean |
| DATE | LocalDate | date |
| DATETIME | LocalDateTime | date |
| TEXT | String | text |

> **注**：低代码模式下未创建显式 mapping，使用 ES 动态映射；如需精确控制类型，请通过类型化仓储 + `@Document` / `@Field` 注解定义 PO。

## 注意事项

1. **索引创建**：低代码初始化时若索引不存在会自动创建，已存在则跳过；不创建显式 mapping，依赖 ES 动态映射
2. **更新策略**：`doUpdate` 采用 "先删除后索引" 方式实现，并非部分更新（`UpdateQuery`），可能导致瞬时不可查
3. **ID 类型**：ES 文档 ID 统一为 `String`，所有 ID 通过 `String.valueOf()` 转换
4. **条件查询**：当前仅支持等值查询（`Criteria.is`），暂不支持范围、全文检索等复杂查询；如需复杂搜索请自定义 Delegate
5. **批量操作**：`saveBatch` / `removeBatchByIds` / `listByIds` 均为循环单条操作，未使用 `bulk` API，大批量场景需评估性能
6. **分页排序**：默认使用 `Sort.unsorted()`，暂未支持通过 `ReqPage` 传递排序字段
7. **深度分页**：当前使用 `PageRequest` from/size 分页，超过 10000 条需通过 `search_after` 或 `scroll` API，建议业务侧限制
8. **全量查询**：`queryList(null)` 与 `queryPage` 使用 `Criteria.where("*").exists()` 或 `Criteria.where("_id").exists()` 匹配全部，性能取决于索引规模
9. **刷新策略**：ES 默认 1 秒刷新，写后立即读可能查不到，如需立即读到可通过 `RefreshPolicy.IMMEDIATE` 配置 `ElasticsearchOperations`
10. **PO 复用**：与其他 starter 共享同一 PO 时，需注意 `@Document` 等 ES 注解在非 ES 环境下应可被忽略

## 许可证

本项目遵循 Apache License 2.0
