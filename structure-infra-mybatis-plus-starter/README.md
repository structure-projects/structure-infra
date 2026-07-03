# structure-infra-mybatis-plus-starter

[MyBatis-Plus](https://baomidou.com/) 接入 `structure-pro-infra` 仓储抽象层的适配模块，提供：

1. **类型化仓储**：通过 `RepositoryFacade` + `MybatisPlusRepositoryDelegate` 透明地以 MyBatis-Plus `BaseMapper` 操作 PO
2. **低代码 MySQL/H2 存储**：通过 `LowCodeStorage` 实现基于 MyBatis `SqlSession` 的动态表/SQL，无需定义实体类

## 功能特性

### 类型化仓储层

- **自动 delegate 创建**：当 `RepositoryFacade` 需要某个 PO 的 delegate 但无用户自定义 bean 时，`MybatisPlusDelegateFactory` 自动发现匹配的 `BaseMapper` 并实例化 `MybatisPlusRepositoryDelegate`
- **自定义 delegate 支持**：用户可继承 `MybatisPlusRepositoryDelegate` 并标注 `@DelegateFor`，`MybatisPlusDelegateBeanPostProcessor` 会自动注入 `BaseMapper` 与 PO 类型
- **约定优于配置的 Mapper 发现**：从 PO 类名推导 Mapper 类（`xxx.po.UserPO` → `xxx.mapper.UserMapper`，`PO` 后缀替换为 `Mapper`）；失败时回退到 bean 名称后缀匹配
- **CQRS 支持**：可与 `ElasticsearchRepositoryDelegate` 等组合实现读写分离

### 低代码 MySQL/H2 存储层

- **完整 `LowCodeStorage` 实现**：基于 MyBatis `SqlSession` 执行动态 SQL
- **多方言支持**：自动检测 MySQL、H2、Oracle、PostgreSQL、SQL Server，适配 DDL、类型映射、分页语法
- **自动 DDL**：注册资源时生成 `CREATE TABLE IF NOT EXISTS`，包含列类型、`NOT NULL`、`DEFAULT`、`AUTO_INCREMENT`、主键、唯一键、索引
- **拦截器友好**：通过 `MappedStatement` + `XMLLanguageDriver` 动态注册 SQL，所有 CRUD 走 MyBatis 拦截器链（分页、数据权限、SQL 日志等）
- **自动填充**：支持 `CREATE` / `UPDATE` / `CREATE_UPDATE` 类型的 `DATETIME` / `DATE` 字段自动填充
- **多方言分页**：MySQL/H2 `LIMIT ... OFFSET`、Oracle `ROWNUM` 子查询、PostgreSQL/SQL Server `OFFSET ... FETCH NEXT`

## 依赖

```xml
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-mybatis-plus-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

模块自身依赖：

- `cn.structured:structure-infra-starter` — 仓储抽象与低代码 API
- `cn.structured:structure-mybatis-plus-starter` — MyBatis-Plus 基础 boot 支持
- `cn.structured:structure-common` — `ReqPage` / `ResPage` / `ICrudRepository`
- `cn.structured:structure-security-core` — 安全集成（数据权限）
- `cn.structured:structure-tenant-starter` — 多租户支持
- `com.baomidou:mybatis-plus-spring-boot4-starter`
- `com.baomidou:mybatis-plus-jsqlparser`

## 自动配置

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册：

```
cn.structure.infra.mybatis.plus.configuration.MybatisPlusAutoConfiguration
cn.structure.infra.mybatis.plus.lowcode.configuration.MybatisPlusLowCodeAutoConfiguration
```

| 自动配置 | 激活条件 | 注册的 Bean |
|---------|---------|------------|
| `MybatisPlusAutoConfiguration` | classpath 存在 `BaseMapper`，且 `structure.infra.type=MYBATIS_PLUS`（默认开启 `matchIfMissing=true`） | `MybatisPlusDelegateFactory`、`MybatisPlusDelegateBeanPostProcessor` |
| `MybatisPlusLowCodeAutoConfiguration` | `structure.infra.lowcode.enabled=true`（默认开启） | `MySqlLowCodeRepoFactory`（注入 `SqlSessionFactory`） |

## 核心类

### 类型化仓储层

#### `MybatisPlusRepositoryDelegate<T, ID>`

实现 `RepositoryDelegate<T, ID>`，包装 `BaseMapper<T>` 提供：

- `save` / `removeById` / `saveBatch` / `removeBatchByIds` — 写操作
- `findById` / `queryById` / `queryByIdOptional` / `queryOne` / `queryOneOptional` / `queryList` / `queryPage` / `listByIds` / `count` / `exists` — 读操作
- 通过反射读取条件对象的非空字段构建 `QueryWrapper`（驼峰转下划线）
- 支持无参构造 + setter，便于子类被 `@DelegateFor` 标注后由后置处理器注入

#### `MybatisPlusDelegateFactory`

实现 `RepositoryDelegateFactory`，`getType()` 返回 `RepositoryType.MYBATIS_PLUS`。`createDelegate(poClass, idClass)`：

1. 按约定推导 Mapper 类（`po` 包段替换为 `mapper`，`PO` 后缀替换为 `Mapper`）
2. 从 `ApplicationContext` 查找该 Mapper bean
3. 找到则返回 `new MybatisPlusRepositoryDelegate<>(mapper, poClass)`，否则返回 `null`

#### `MybatisPlusDelegateBeanPostProcessor`

`BeanPostProcessor`，对每个 `instanceof MybatisPlusRepositoryDelegate` 且带 `@DelegateFor` 注解的 bean：

- 根据 `@DelegateFor.po()` 解析 Mapper bean
- 调用 `setBaseMapper(mapper)` 与 `setEntityClass(poClass)` 完成注入

### 低代码存储层

#### `MySqlLowCodeRepoFactory`

实现 `LowCodeRepoFactory`，`getType()` 返回 `StorageType.MYSQL`，`createStorage(schema, config)` 返回 `new MySqlLowCodeStorage(schema, sqlSessionFactory)`。

#### `MySqlLowCodeStorage`

实现 `LowCodeStorage`，关键行为：

- **方言检测**：`detectDialect()` 通过 `Connection.getMetaData().getDatabaseProductName()` 识别 MySQL / H2 / Oracle / PostgreSQL / SQL Server
- **自动 DDL**：`initialize()` 调用 `buildCreateTableSql()` 生成 `CREATE TABLE IF NOT EXISTS`，MySQL 附加 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`
- **字段类型映射**：`STRING → VARCHAR(length)`、`LONG → BIGINT`、`INTEGER → INT`、`BOOLEAN → TINYINT(1)` (MySQL) / `BOOLEAN` (其他)、`DECIMAL → DECIMAL(p,s)`、`DATETIME → DATETIME` (MySQL) / `TIMESTAMP` (其他)、`DATE → DATE`、`TEXT → TEXT`、`JSON → JSON` (MySQL) / `TEXT` (其他)
- **拦截器友好执行**：`executeSelect` / `executeUpdate` / `registerCountStatement` 动态构建 `MappedStatement`，使用 `XMLLanguageDriver` 解析 `<script>...</script>` SQL，注册到 `Configuration` 执行后从 `finally` 块移除
- **自增主键**：当 id 字段为 `autoIncrement` 时，使用原生 JDBC `PreparedStatement(..., RETURN_GENERATED_KEYS)` 获取生成的主键
- **显式列列表**：`buildSelectColumns()` 拼接 schema 字段名，避免 `SELECT *`
- **行归一化**：H2/Oracle 返回大写列名，`normalizeRow` 将所有 key 转小写
- **自动填充**：`fillAutoFields(data, fillType)` 处理 `CREATE` / `UPDATE` / `CREATE_UPDATE`，使用 `putIfAbsent` 保证调用方值优先
- **多方言分页**：`buildPaginationSql(baseSql, pageNum, pageSize)`
  - MySQL / H2：`... LIMIT pageSize OFFSET offset`
  - Oracle：嵌套 `ROWNUM` 子查询
  - PostgreSQL / SQL Server：`... OFFSET offset ROWS FETCH NEXT pageSize ROWS ONLY`

## 配置属性

本模块自身不定义专属配置属性，通过标准 Spring Boot 配置驱动：

```yaml
structure:
  infra:
    type: MYBATIS_PLUS                  # 默认开启 MybatisPlusAutoConfiguration
    lowcode:
      enabled: true                     # 默认开启 MybatisPlusLowCodeAutoConfiguration

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto
```

低代码资源定义示例：

```yaml
structure:
  infra:
    lowcode:
      enabled: true
      resources:
        lc_user:
          schema:
            table-name: t_lowcode_user
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
                index: true
              status:
                type: string
                length: 16
                default-value: active
              created_at:
                type: datetime
                auto-fill: create
              updated_at:
                type: datetime
                auto-fill: create_update
          repository:
            type: mysql
```

## 使用示例

### 类型化仓储

#### 1. 定义 PO 与 Mapper

```java
@Data
@TableName("t_user")
public class UserPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
    private Integer age;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

@Mapper
public interface UserMapper extends BaseMapper<UserPO> { }
```

#### 2. 定义领域实体与仓储接口

```java
@Data
public class UserEntity {
    private Long id;
    private String username;
    private String email;
    private Integer age;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

public interface UserRepository extends ICrudRepository<UserEntity, Long> {
    UserEntity findByName(String name);
}
```

#### 3. 定义 delegate 接口（可选，用于自定义查询）

```java
public interface UserRepositoryDelegate extends RepositoryDelegate<UserPO, Long> {
    UserPO finByName(String name);
}
```

#### 4. 定义抽象 Facade 基类

```java
public abstract class AbstractUserRepositoryImpl
        extends RepositoryFacade<UserEntity, Long, UserPO, UserRepositoryDelegate>
        implements UserRepository {

    @Override
    public UserEntity findByName(String name) {
        return toEntity(this.baseDelegate.finByName(name));
    }
}
```

#### 5. 定义具体仓储（标注 `@Repository`）

```java
@Repository(value = "用户仓储", type = RepositoryType.MYBATIS_PLUS,
            entity = UserEntity.class, po = UserPO.class)
@Component("userRepository")
public class UserRepositoryImpl extends AbstractUserRepositoryImpl {
}
```

#### 6. 提供自定义 delegate（可选，用于自定义查询）

```java
@Component
@DelegateFor(
    name = "userRepository",
    type = RepositoryType.MYBATIS_PLUS,
    po = UserPO.class,
    description = "用户仓储 MyBatis Plus 实现",
    priority = 10
)
@AllArgsConstructor
public class UserMybatisPlusDelegate
        extends MybatisPlusRepositoryDelegate<UserPO, Long>
        implements UserRepositoryDelegate {

    private final UserMapper userMapper;

    @Override
    public UserPO finByName(String name) {
        return userMapper.selectOne(
            Wrappers.<UserPO>lambdaQuery().eq(UserPO::getUsername, name));
    }
}
```

`MybatisPlusDelegateBeanPostProcessor` 会基于 `@DelegateFor.po()` 自动调用 `setBaseMapper(userMapper)` 与 `setEntityClass(UserPO.class)`，因此继承的 CRUD 方法开箱即用。

> 若未提供自定义 delegate，`MybatisPlusDelegateFactory.createDelegate(UserPO.class, Long.class)` 会按约定发现 `UserMapper` 并自动创建 `MybatisPlusRepositoryDelegate`。

#### 7. 业务层使用

```java
@Autowired
private UserRepository userRepository;

UserEntity saved = userRepository.save(userEntity);
UserEntity found = userRepository.findById(saved.getId());
UserEntity one   = userRepository.queryOne(conditionEntity);
Optional<UserEntity> opt = userRepository.queryByIdOptional(id);
List<UserEntity> all = userRepository.queryList(null);

ReqPage reqPage = new ReqPage();
reqPage.setPage(2);
reqPage.setSize(5);
ResPage<UserEntity> page = userRepository.queryPage(reqPage);

userRepository.removeById(id);
```

### 低代码仓储

#### YAML 声明资源

```yaml
structure:
  infra:
    lowcode:
      enabled: true
      resources:
        lc_user:
          schema:
            table-name: t_lowcode_user
            fields:
              id: { type: long, primary-key: true, auto-increment: true }
              username: { type: string, length: 64, nullable: false, index: true }
              email: { type: string, length: 128, index: true }
              age: { type: int }
              status: { type: string, length: 16, default-value: active }
              created_at: { type: datetime, auto-fill: create }
              updated_at: { type: datetime, auto-fill: create_update }
          repository:
            type: mysql
```

#### 代码使用

```java
@Autowired
private LowCodeRepository lowCodeRepository;

private static final String RESOURCE_NAME = "lc_user";

// 保存（自动填充 created_at、updated_at、status 默认值）
Map<String, Object> user = new HashMap<>();
user.put("username", "zhangsan");
user.put("email", "zhangsan@example.com");
user.put("age", 25);
Map<String, Object> saved = lowCodeRepository.save(RESOURCE_NAME, user);

// 更新（id 存在则 upsert）
saved.put("email", "updated@test.com");
lowCodeRepository.save(RESOURCE_NAME, saved);

// 查询
Map<String, Object> found = lowCodeRepository.findById(RESOURCE_NAME, id);
Map<String, Object> one    = lowCodeRepository.queryOne(RESOURCE_NAME, conditionMap);
Optional<Map<String, Object>> opt = lowCodeRepository.queryByIdOptional(RESOURCE_NAME, id);
List<Map<String, Object>> list   = lowCodeRepository.queryList(RESOURCE_NAME, conditionMap);

// 分页
ReqPage reqPage = new ReqPage();
reqPage.setPage(2);
reqPage.setSize(5);
ResPage<Map<String, Object>> page = lowCodeRepository.queryPage(RESOURCE_NAME, reqPage);

// 批量与计数
lowCodeRepository.saveBatch(RESOURCE_NAME, listOfMaps);
lowCodeRepository.removeBatchByIds(RESOURCE_NAME, listOfIds);
lowCodeRepository.listByIds(RESOURCE_NAME, listOfIds);
long total = lowCodeRepository.count(RESOURCE_NAME, conditionMap);
boolean exists = lowCodeRepository.exists(RESOURCE_NAME, conditionMap);
```

## 低代码装配流程

1. `MybatisPlusLowCodeAutoConfiguration` 注册 `MySqlLowCodeRepoFactory(sqlSessionFactory)` bean
2. `LowCodeAutoConfiguration`（在 `structure-infra-starter`）收集所有 `LowCodeRepoFactory` bean，按 `StorageType` 索引
3. 对每个 YAML 声明的资源，`ResourceSchemaBuilder` 构建 `ResourceSchema` 与 `RepositoryConfig`，调用 `router.registerResource(...)`
4. 路由器请求 `MySqlLowCodeRepoFactory.createStorage(schema, config)` 创建 `MySqlLowCodeStorage`
5. 调用 `initialize()` 执行 `CREATE TABLE IF NOT EXISTS` DDL
6. 后续 `LowCodeRepository` 调用被路由到该 `MySqlLowCodeStorage` 实例

## 测试

参考示例模块 `structure-infra-sample-mybatis`（使用 H2 内存数据库）：

```bash
# 类型化仓储测试
mvn test -pl structure-infra-sample/structure-infra-sample-mybatis \
  -Dtest="cn.structure.infra.sample.repository.UserRepositoryTest"

# 低代码测试
mvn test -pl structure-infra-sample/structure-infra-sample-mybatis \
  -Dtest="cn.structure.infra.sample.repository.LowCodeRepositoryTest"
```

## License

Apache License 2.0
