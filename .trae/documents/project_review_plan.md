# 项目设计与实现检查方案

## 一、架构设计评估

### 1.1 当前架构问题分析

当前项目采用 **Facade + Delegate + CQRS** 的分层架构，但存在以下架构性问题：

| 问题                                    | 影响                                                         |
| ------------------------------------- | ---------------------------------------------------------- |
| 依赖手动装配流程（RepositoryBeanPostProcessor） | Spring Bean 初始化顺序问题，依赖 Facade 的 Bean 在初始化时 delegate 为 null |
| 依赖 @Repository 和 @DelegateFor 注解      | 增加了学习成本和配置复杂度                                              |
| 依赖 \*DelegateBeanPostProcessor 进行依赖注入 | 违背 Spring 自动装配原则                                           |
| Delegate 创建时需要传入类型参数                  | 重复代码，类型信息应该从泛型自解析                                          |

### 1.2 目标架构：简化为纯 Spring DI 模式

```
┌─────────────────────────────────────────────────────────────┐
│                     Domain Layer                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  UserRepository (接口) ← ICrudRepository<T, ID>     │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│                    Facade Layer (ACL)                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ RepositoryFacade<UserEntity, Long>                  │   │
│  │   └── @Autowired RepositoryDelegate<E, ID> delegate│   │
│  └─────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ CqrsRepositoryFacade<E, ID, D, RD>                 │   │
│  │   ├── @Autowired D baseDelegate                     │   │
│  │   └── @Autowired @ReadDelegate RD readDelegate     │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│                    Delegate Layer                           │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ RepositoryDelegate<UserEntity, Long>                │   │
│  │   ├── @PostConstruct resolveGenericTypes()          │   │
│  │   └── @Autowired EntityManager / BaseMapper         │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│              Infrastructure Layer                           │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ @ConditionalOnMissingBean 提供默认 Delegate          │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 1.3 简化原则

| 原则                                   | 说明                                             |
| ------------------------------------ | ---------------------------------------------- |
| **移除所有 BeanPostProcessor**           | 使用 Spring 原生 DI 替代手动装配                         |
| **移除 @Repository 和 @DelegateFor 注解** | 通过泛型类型匹配自动装配                                   |
| **Delegate 自解析泛型类型**                 | 在 @PostConstruct 中通过反射获取 Entity/PO/ID 类型       |
| **依赖 Spring @Autowired**             | Facade 自动注入 Delegate，Delegate 自动注入基础设施依赖       |
| **@ConditionalOnMissingBean 回退**     | 当没有用户自定义 Delegate 时，由 AutoConfiguration 提供默认实现 |

***

## 二、问题分类与修正方案

### 2.1 架构重构（核心变更）

#### 问题1：移除手动装配流程，使用 Spring 原生 DI

**问题描述**：
当前通过 `RepositoryBeanPostProcessor.onApplicationEvent(ContextRefreshedEvent)` 进行延迟绑定，导致 Bean 初始化顺序问题。

**受影响文件**：

* [RepositoryBeanPostProcessor.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryBeanPostProcessor.java)

**修正方案**：

1. **删除 RepositoryBeanPostProcessor**：完全移除这个类
2. **RepositoryFacade 使用 @Autowired**：在 `RepositoryDelegate` 字段上添加 `@Autowired` 注解
3. **Spring 泛型类型匹配**：Spring 会自动根据泛型参数 `RepositoryDelegate<UserEntity, Long>` 匹配正确的 Delegate Bean

***

#### 问题2：移除 @Repository 和 @DelegateFor 注解

**问题描述**：
`@Repository` 和 `@DelegateFor` 注解增加了配置复杂度，类型信息应该通过泛型自动获取。

**受影响文件**：

* [Repository.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/annotations/Repository.java)

* [DelegateFor.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/annotations/DelegateFor.java)

**修正方案**：

1. **删除 @Repository 注解**：Facade 类只需要 `@Component` 注解
2. **删除 @DelegateFor 注解**：Delegate 类只需要 `@Component` 注解
3. **类型信息从泛型提取**：Delegate 在 `@PostConstruct` 中自解析泛型类型

***

#### 问题3：移除所有 \*DelegateBeanPostProcessor

**问题描述**：
`JpaDelegateBeanPostProcessor`、`MongoDelegateBeanPostProcessor`、`ElasticsearchDelegateBeanPostProcessor` 等类负责注入 EntityManager、BaseMapper 等依赖，违背了 Spring 自动装配原则。

**受影响文件**：

* [JpaDelegateBeanPostProcessor.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-jpa-starter/src/main/java/cn/structure/infra/jpa/repository/JpaDelegateBeanPostProcessor.java)

* [MongoDelegateBeanPostProcessor.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-mongodb-starter/src/main/java/cn/structure/infra/mongodb/repository/MongoDelegateBeanPostProcessor.java)

* [ElasticsearchDelegateBeanPostProcessor.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-elasticsearch-starter/src/main/java/cn/structure/infra/elasticsearch/repository/ElasticsearchDelegateBeanPostProcessor.java)

* [MybatisPlusDelegateBeanPostProcessor.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-mybatis-plus-starter/src/main/java/cn/structure/infra/mybatis/plus/repository/MybatisPlusDelegateBeanPostProcessor.java)

**修正方案**：

1. \**删除所有 DelegateBeanPostProcessor 类*
2. **Delegate 使用 @Autowired 注入依赖**：在 EntityManager、BaseMapper 等字段上添加 `@Autowired` 注解
3. **Delegate 使用 @PostConstruct 自解析类型**：在初始化时通过反射解析泛型参数

***

#### 问题4：Delegate 自解析泛型类型（不再通过构造函数传入）

**问题描述**：
当前 Delegate 创建时需要通过构造函数或 setter 传入 entityClass、poClass、idClass，应该改为内部自解析。

**受影响文件**：

* [JpaRepositoryDelegate.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-jpa-starter/src/main/java/cn/structure/infra/jpa/repository/JpaRepositoryDelegate.java#L52-L81)

* [MybatisPlusRepositoryDelegate.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-mybatis-plus-starter/src/main/java/cn/structure/infra/mybatis/plus/repository/MybatisPlusRepositoryDelegate.java#L53-L90)

* [MongoRepositoryDelegate.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-mongodb-starter/src/main/java/cn/structure/infra/mongodb/repository/MongoRepositoryDelegate.java)

* [ElasticsearchRepositoryDelegate.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-elasticsearch-starter/src/main/java/cn/structure/infra/elasticsearch/repository/ElasticsearchRepositoryDelegate.java)

**修正方案**：

1. **添加 @PostConstruct resolveGenericTypes() 方法**：在每个 Delegate 基类中添加泛型类型自解析方法
2. **移除带参数的构造函数**：保留无参构造函数，依赖 `@Autowired` 和 `@PostConstruct`
3. **提取通用工具方法**：创建 `GenericTypeResolver` 工具类，提供统一的泛型类型提取逻辑

***

#### 问题5：CQRS 模式下双代理注入

**问题描述**：
`CqrsRepositoryFacade` 需要注入两个不同类型的 Delegate（baseDelegate 和 readDelegate），Spring 默认无法区分。

**受影响文件**：

* [CqrsRepositoryFacade.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/repository/CqrsRepositoryFacade.java#L52-L72)

**修正方案**：

1. **创建 @ReadDelegate 限定注解**：用于标记读代理 Bean
2. **CqrsRepositoryFacade 使用 @Qualifier**：

   * `@Autowired D baseDelegate`：匹配普通的 RepositoryDelegate

   * `@Autowired @ReadDelegate RD readDelegate`：匹配带有 @ReadDelegate 注解的 Bean
3. **用户自定义 Delegate 时添加 @ReadDelegate**：用于标记读代理

***

#### 问题6：默认 Delegate 自动创建

**问题描述**：
当没有用户自定义 Delegate 时，需要自动创建默认实现。

**受影响文件**：

* [JpaDelegateFactory.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-jpa-starter/src/main/java/cn/structure/infra/jpa/repository/JpaDelegateFactory.java)

* [JpaAutoConfiguration.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-jpa-starter/src/main/java/cn/structure/infra/jpa/configuration/JpaAutoConfiguration.java)

**修正方案**：

1. **删除 RepositoryDelegateFactory 接口**：不再需要这个 SPI
2. **AutoConfiguration 中使用 @ConditionalOnMissingBean**：为每种存储类型提供默认 Delegate Bean
3. **泛型类型通过 @PostConstruct 自解析**：默认 Delegate 创建后自动解析类型

***

### 2.2 性能问题（建议优化）

#### 问题7：JPA 实现存在严重性能问题

**问题描述**：
`JpaRepositoryDelegate` 存在多处性能问题：

* `queryPage()`：先 `findAll()` 获取全量数据，再进行内存分页

* `count()`：调用 `queryList().size()`，执行完整查询后统计数量

* `listByIds()`：对每个 ID 单独查询，产生 N+1 问题

**受影响文件**：

* [JpaRepositoryDelegate.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-jpa-starter/src/main/java/cn/structure/infra/jpa/repository/JpaRepositoryDelegate.java#L148-L262)

**修正方案**：

1. `queryPage()`：使用 `CriteriaQuery` + `setFirstResult`/`setMaxResults` 实现数据库分页
2. `count()`：使用 `CriteriaBuilder.count()` 实现 COUNT 查询
3. `listByIds()`：使用 `CriteriaBuilder.in()` 实现批量查询

***

### 2.3 代码规范问题

#### 问题8：目录拼写错误

**问题描述**：
CQRS 示例工程中存在目录拼写错误：`repositoory` 应为 `repository`。

**受影响文件**：

* 目录路径：`structure-infra-sample-cqrs/src/main/java/cn/structure/infra/sample/cqrs/infra/repositoory/`

**修正方案**：

1. 重命名目录为 `repository`
2. 更新所有相关文件的包声明
3. 更新 `pom.xml` 和其他引用该路径的配置文件

***

## 三、修正优先级

| 优先级    | 问题                                   | 原因                |
| ------ | ------------------------------------ | ----------------- |
| **P0** | 问题1 - 移除手动装配流程                       | 架构性问题，影响所有组件      |
| **P0** | 问题2 - 移除 @Repository 和 @DelegateFor  | 架构性问题，简化 API      |
| **P0** | 问题3 - 移除 \*DelegateBeanPostProcessor | 架构性问题，简化依赖注入      |
| **P0** | 问题4 - Delegate 自解析泛型类型               | 架构性问题，类型获取方式变更    |
| **P0** | 问题5 - CQRS 双代理注入                     | 架构性问题，CQRS 模式核心功能 |
| **P0** | 问题6 - 默认 Delegate 自动创建               | 架构性问题，自动装配回退机制    |
| **P2** | 问题7 - JPA 性能                         | 影响大数据量场景性能        |
| **P3** | 问题8 - 目录拼写                           | 代码规范问题            |

***

## 四、修正步骤

### 阶段一：核心架构重构（P0）

#### 1.1 创建通用工具类

1. 创建 `GenericTypeResolver` 工具类，提供泛型类型提取方法：

   * `resolveTypes(Class<?> clazz, Class<?> targetInterface)`：提取指定接口的泛型参数

#### 1.2 修改 Delegate 基类

1. **JpaRepositoryDelegate**：

   * 添加 `@Autowired` 注解到 `entityManager` 字段

   * 添加 `@PostConstruct` 方法，调用 `resolveGenericTypes()` 自解析 Entity/PO/ID 类型

   * 移除带参数的构造函数，保留无参构造函数

2. **MybatisPlusRepositoryDelegate**：

   * 添加 `@Autowired` 注解到 `baseMapper` 字段

   * 添加 `@PostConstruct` 方法，调用 `resolveGenericTypes()` 自解析 Entity/PO/ID 类型

   * 移除带参数的构造函数，保留无参构造函数

3. **MongoRepositoryDelegate**：

   * 添加 `@Autowired` 注解到 `MongoTemplate` 字段

   * 添加 `@PostConstruct` 方法，调用 `resolveGenericTypes()` 自解析类型

4. **ElasticsearchRepositoryDelegate**：

   * 添加 `@Autowired` 注解到 `RestHighLevelClient` 字段

   * 添加 `@PostConstruct` 方法，调用 `resolveGenericTypes()` 自解析类型

#### 1.3 修改 Facade 类

1. **RepositoryFacade**：

   * 在 `delegate` 字段上添加 `@Autowired` 注解

   * 修改构造函数，移除 delegate 参数

2. **CqrsRepositoryFacade**：

   * 在 `baseDelegate` 字段上添加 `@Autowired` 注解

   * 在 `readDelegate` 字段上添加 `@Autowired @ReadDelegate` 注解

   * 修改构造函数，移除 delegate 参数

#### 1.4 创建 @ReadDelegate 限定注解

1. 创建 `@ReadDelegate` 注解，用于标记读代理 Bean

#### 1.5 修改 AutoConfiguration

1. **JpaAutoConfiguration**：

   * 删除 `JpaDelegateFactory` Bean

   * 删除 `JpaDelegateBeanPostProcessor` Bean

   * 添加默认 `JpaRepositoryDelegate` Bean，使用 `@ConditionalOnMissingBean`

2. **MongoAutoConfiguration**：

   * 删除 `MongoDelegateFactory` Bean

   * 删除 `MongoDelegateBeanPostProcessor` Bean

   * 添加默认 `MongoRepositoryDelegate` Bean，使用 `@ConditionalOnMissingBean`

3. **ElasticsearchAutoConfiguration**：

   * 删除 `ElasticsearchDelegateFactory` Bean

   * 删除 `ElasticsearchDelegateBeanPostProcessor` Bean

   * 添加默认 `ElasticsearchRepositoryDelegate` Bean，使用 `@ConditionalOnMissingBean`

4. **MybatisPlusAutoConfiguration**：

   * 删除 `MybatisPlusDelegateFactory` Bean

   * 删除 `MybatisPlusDelegateBeanPostProcessor` Bean

   * 添加默认 `MybatisPlusRepositoryDelegate` Bean，使用 `@ConditionalOnMissingBean`

#### 1.6 删除废弃的类

1. 删除 `RepositoryBeanPostProcessor`
2. 删除 `RepositoryDelegateFactory` 接口
3. 删除 `@Repository` 注解
4. 删除 `@DelegateFor` 注解
5. 删除所有 `*DelegateBeanPostProcessor` 类

#### 1.7 更新示例代码

1. **UserRepositoryImpl**：

   * 移除 `@Repository` 注解（如果存在）

   * 确保继承 `RepositoryFacade` 并使用泛型参数

2. **UserCqrsRepository**：

   * 移除 `@Repository` 注解

   * 确保读代理类添加 `@ReadDelegate` 注解

3. **UserMybatisPlusDelegate**：

   * 移除 `@DelegateFor` 注解

   * 添加 `@Component` 注解

4. **UserWriteDelegate / UserReadDelegate**：

   * 移除 `@DelegateFor` 注解

   * 添加 `@Component` 注解

   * `UserReadDelegate` 添加 `@ReadDelegate` 注解

### 阶段二：性能优化（P2）

1. 优化 `JpaRepositoryDelegate` 的分页、count 和 listByIds 方法

### 阶段三：代码规范（P3）

1. 修复目录拼写错误

***

## 五、验证方案

### 5.1 编译验证

```bash
mvn clean compile -DskipTests
```

### 5.2 单元测试验证

```bash
# MyBatis 模块测试
mvn test -pl structure-infra-sample/structure-infra-sample-mybatis

# JPA 模块测试
mvn test -pl structure-infra-sample/structure-infra-sample-jpa

# MongoDB 模块测试
mvn test -pl structure-infra-sample/structure-infra-sample-mongodb

# CQRS 模块测试
mvn test -pl structure-infra-sample/structure-infra-sample-cqrs
```

### 5.3 集成测试验证

```bash
# 运行所有测试
mvn test
```

### 5.4 手动验证要点

1. 验证 Facade 正确返回领域实体类型
2. 验证 Entity ↔ PO 转换正常工作
3. 验证 CQRS 模式下读写操作正确路由
4. 验证找不到 Delegate 时自动创建默认实现
5. 验证 Bean 初始化顺序问题已修复（依赖 Facade 的 Bean 能正常初始化）
6. 验证默认 Delegate 使用 @ConditionalOnMissingBean 正确工作

***

## 六、风险与依赖考虑

### 6.1 风险评估

| 风险                                  | 级别 | 缓解措施                         |
| ----------------------------------- | -- | ---------------------------- |
| Spring 泛型类型匹配可能不支持复杂泛型              | 中  | 测试各种泛型场景，必要时使用 @Qualifier 补充 |
| 现有代码大量使用 @Repository 和 @DelegateFor | 高  | 提供迁移指南，逐步替换                  |
| CQRS 模式下双代理注入可能冲突                   | 中  | 使用 @ReadDelegate 限定注解明确区分    |
| 默认 Delegate 自解析泛型失败                 | 低  | 添加详细日志和异常处理                  |

### 6.2 依赖变更

* 移除 `RepositoryBeanPostProcessor` 相关依赖

* 移除 `*DelegateBeanPostProcessor` 相关依赖

* 移除 `@Repository` 和 `@DelegateFor` 注解依赖

* 添加 `@ReadDelegate` 限定注解

### 6.3 向后兼容性

* **不兼容变更**：移除了 `@Repository`、`@DelegateFor` 注解和所有 BeanPostProcessor

* **兼容变更**：Facade 和 Delegate 的使用方式保持不变，只是配置方式简化

***

## 七、简化后的代码示例

### 7.1 RepositoryFacade

```java
@Component
public class UserRepositoryImpl extends RepositoryFacade<UserEntity, Long> implements UserRepository {
    // @Autowired 自动注入 RepositoryDelegate<UserEntity, Long>
    
    @Override
    public UserEntity findByName(String name) {
        return ((UserRepositoryDelegate) getDelegate()).findByName(name);
    }
}
```

### 7.2 CqrsRepositoryFacade

```java
@Component
public class UserCqrsRepository extends CqrsRepositoryFacade<UserEntity, Long, UserWriteDelegate, UserReadDelegate> implements UserRepository {
    // @Autowired 自动注入 UserWriteDelegate
    // @Autowired @ReadDelegate 自动注入 UserReadDelegate
}
```

### 7.3 Delegate

```java
@Component
public class UserMybatisPlusDelegate extends MybatisPlusRepositoryDelegate<UserEntity, MybatisUserPO, Long> implements UserRepositoryDelegate {
    
    @Autowired
    private UserMapper userMapper;
    
    @Override
    public UserEntity findByName(String name) {
        MybatisUserPO po = userMapper.selectOne(Wrappers.<MybatisUserPO>lambdaQuery().eq(MybatisUserPO::getUsername, name));
        return toEntity(po);
    }
}
```

### 7.4 读代理（CQRS）

```java
@Component
@ReadDelegate
public class UserReadDelegate extends ElasticsearchRepositoryDelegate<UserEntity, UserEntity, Long> implements UserRepositoryDelegate {
    // 只读操作实现
}
```

### 7.5 AutoConfiguration 示例

```java
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
public class JpaAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public JpaRepositoryDelegate<?, ?, ?> jpaRepositoryDelegate(EntityManager entityManager) {
        return new JpaRepositoryDelegate<>();
        // 泛型类型在 @PostConstruct 中自解析
    }
}
```

MongoRepositoryDelegate 这个不需要单独创建bean,因为他们是基础类和mabtis 使用方法类似。他应该是与子类创建时初始化。

MybatisPlusRepositoryDelegate.java 63-70 这个实现方式我并不满意应该在他的默认构造方法中初始化不应该使用spring 的注解，这个不对。

ongoRepositoryDelegate.java 55-73 甚至你可以理解没有这些属性但是可以有get方法来获取这个类型。

你不应该移除代码注释