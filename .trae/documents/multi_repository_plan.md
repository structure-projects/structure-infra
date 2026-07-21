# 多类型仓库支持实现计划

## 一、需求分析

基于用户需求和现有代码库结构，需要实现以下功能：

| 需求点 | 描述 |
|-------|------|
| 多仓库门面类 | 新建一个继承 `RepositoryFacade` 的门面类，支持多种仓储类型切换 |
| 仓库类型注解 | 新建注解用于标记仓库类型，业务侧可使用 |
| 自动装配机制 | 项目启动时将所有支持的仓库类型装配到多仓库门面中 |
| 混合实现支持 | 支持默认仓库和多仓库的混合使用 |

## 二、架构设计

### 2.1 核心组件

```
┌─────────────────────────────────────────────────────────────────┐
│                    业务层 (Service)                              │
├─────────────────────────────────────────────────────────────────┤
│           RepositoryFacade / CqrsRepositoryFacade /             │
│              MultiRepositoryFacade (多仓库门面)                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  @RepositoryType(MYBATIS_PLUS) → MybatisPlusDelegate       │  │
│  │  @RepositoryType(JPA)          → JpaDelegate               │  │
│  │  @RepositoryType(MONGODB)      → MongoDelegate             │  │
│  │  @RepositoryType(ELASTICSEARCH) → ElasticsearchDelegate    │  │
│  │  默认路由 → defaultDelegate                                 │  │
│  └───────────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│              RepositoryTypeContext (线程上下文)                  │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 类继承关系

```
RepositoryFacade<T, ID, D extends RepositoryDelegate<T, ID>>
    │
    ├── CqrsRepositoryFacade<T, ID, D, RD>    // CQRS 读写分离
    │     └── delegate: D (写代理)
    │     └── readDelegate: RD (读代理)
    │
    └── MultiRepositoryFacade<T, ID, D>       // 多数据源切换
          └── delegates: Map<RepositoryType, D>
          └── defaultDelegate: D
```

### 2.3 设计决策

| 决策点 | 方案 | 理由 |
|-------|------|------|
| 多仓库实现位置 | Facade 层（而非 Delegate 层） | 与 CQRS 设计保持一致，都是 Facade 的子类 |
| 路由机制 | ThreadLocal + 注解 | 支持编程式切换和声明式指定，灵活度高 |
| 注解作用域 | 类级别 + 方法级别 | 支持细粒度控制仓库类型 |
| 默认路由 | 配置指定默认类型 | 兼容现有单仓库模式 |
| 装配方式 | BeanPostProcessor | 复用现有装配机制，保持一致性 |

## 三、实现步骤

### 3.1 新增文件

#### 3.1.1 `@RepositoryType` 注解

**文件路径**: `structure-infra-starter/src/main/java/cn/structure/infra/annotations/RepositoryType.java`

**功能**: 标记仓库类型，可用于：
- Delegate 实现类：标记该 delegate 对应的仓库类型
- Repository 接口：标记该仓库默认使用的仓库类型
- Service 方法：临时切换仓库类型

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RepositoryType {
    RepositoryTypeEnum value();
}
```

#### 3.1.2 `RepositoryTypeEnum` 枚举

**文件路径**: `structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryTypeEnum.java`

**功能**: 扩展现有 `RepositoryType`，用于路由匹配

#### 3.1.3 `MultiRepositoryFacade` 门面类

**文件路径**: `structure-infra-starter/src/main/java/cn/structure/infra/repository/MultiRepositoryFacade.java`

**核心功能**:
- 继承 `RepositoryFacade<T, ID, D>`
- 内部维护 `Map<RepositoryTypeEnum, D>`
- 根据上下文或注解选择 delegate
- 重写所有 CRUD 方法的路由分发

#### 3.1.4 `RepositoryTypeContext` 上下文

**文件路径**: `structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryTypeContext.java`

**功能**:
- ThreadLocal 存储当前仓库类型
- 提供静态方法设置/获取/清除上下文
- 支持 try-with-resources 模式

#### 3.1.5 `MultiRepositoryBeanPostProcessor`

**文件路径**: `structure-infra-starter/src/main/java/cn/structure/infra/repository/MultiRepositoryBeanPostProcessor.java`

**功能**:
- 扫描所有 `RepositoryDelegate` 实现
- 根据 `@RepositoryType` 注解分类
- 注入到 `MultiRepositoryFacade` 中

### 3.2 修改文件

#### 3.2.1 `InfraProperties`

**修改内容**: 添加多仓库相关配置
```java
// 新增配置
private boolean multiRepositoryEnabled = false;
private RepositoryTypeEnum defaultRepositoryType = RepositoryTypeEnum.AUTO;
```

#### 3.2.2 `RepositoryBeanPostProcessor`

**修改内容**: 支持 `MultiRepositoryFacade` 的注入逻辑

#### 3.2.3 各 Delegate 实现类

**修改内容**: 添加 `@RepositoryType` 注解标记类型
- `JpaRepositoryDelegate` → `@RepositoryType(JPA)`
- `MybatisPlusRepositoryDelegate` → `@RepositoryType(MYBATIS_PLUS)`
- `MongoRepositoryDelegate` → `@RepositoryType(MONGODB)`
- `ElasticsearchRepositoryDelegate` → `@RepositoryType(ELASTICSEARCH)`

#### 3.2.4 删除 `MultiRepositoryDelegate`

**删除文件**: `structure-infra-starter/src/main/java/cn/structure/infra/repository/MultiRepositoryDelegate.java`

**原因**: 多仓库切换逻辑应在 Facade 层实现，而非 Delegate 层

## 四、配置示例

### 4.1 YAML 配置

```yaml
structure:
  infra:
    multi-repository-enabled: true
    default-repository-type: MYBATIS_PLUS
```

### 4.2 业务侧使用

```java
// 方式1：使用 MultiRepositoryFacade
public interface UserRepository extends MultiRepositoryFacade<UserEntity, Long, UserRepositoryDelegate> {
    UserEntity findByName(String name);
}

// 方式2：编程式切换
try (RepositoryTypeContext context = RepositoryTypeContext.use(RepositoryTypeEnum.MONGODB)) {
    userRepository.save(user);
}

// 方式3：默认使用配置的仓库类型
userRepository.save(user);
```

### 4.3 自定义方法实现

```java
public abstract class AbstractUserRepositoryImpl extends MultiRepositoryFacade<UserEntity, Long, UserRepositoryDelegate> implements UserRepository {

    @Override
    public UserEntity findByName(String name) {
        UserRepositoryDelegate currentDelegate = getCurrentDelegate();
        return currentDelegate.findByName(name);
    }
}
```

## 五、风险与注意事项

| 风险点 | 应对措施 |
|-------|---------|
| 线程安全 | 使用 ThreadLocal，注意清理 |
| 性能开销 | 路由逻辑简单，可忽略 |
| 类型匹配 | 使用泛型类型检查确保安全性 |
| 事务管理 | 多仓库场景需注意事务一致性 |

## 六、测试计划

| 测试场景 | 描述 |
|---------|------|
| 单仓库模式 | 验证默认仓库类型正常工作 |
| 多仓库模式 | 验证多类型仓库正确路由 |
| 编程式切换 | 验证 ThreadLocal 上下文切换 |
| 注解标记 | 验证类级别和方法级别注解生效 |
| 混合使用 | 验证默认和指定类型混合使用 |

## 七、文件清单

| 操作 | 文件路径 |
|------|---------|
| 新增 | `structure-infra-starter/src/main/java/cn/structure/infra/annotations/RepositoryType.java` |
| 新增 | `structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryTypeEnum.java` |
| 新增 | `structure-infra-starter/src/main/java/cn/structure/infra/repository/MultiRepositoryFacade.java` |
| 新增 | `structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryTypeContext.java` |
| 新增 | `structure-infra-starter/src/main/java/cn/structure/infra/repository/MultiRepositoryBeanPostProcessor.java` |
| 删除 | `structure-infra-starter/src/main/java/cn/structure/infra/repository/MultiRepositoryDelegate.java` |
| 修改 | `structure-infra-starter/src/main/java/cn/structure/infra/properties/InfraProperties.java` |
| 修改 | `structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryBeanPostProcessor.java` |
| 修改 | `structure-infra-jpa-starter/src/main/java/cn/structure/infra/jpa/repository/JpaRepositoryDelegate.java` |
| 修改 | `structure-infra-mybatis-plus-starter/src/main/java/cn/structure/infra/mybatis/plus/repository/MybatisPlusRepositoryDelegate.java` |
| 修改 | `structure-infra-mongodb-starter/src/main/java/cn/structure/infra/mongodb/repository/MongoRepositoryDelegate.java` |
| 修改 | `structure-infra-elasticsearch-starter/src/main/java/cn/structure/infra/elasticsearch/repository/ElasticsearchRepositoryDelegate.java` |
| 修改 | `structure-infra-sample/structure-infra-sample-multi/src/main/java/cn/structure/infra/sample/multi/infra/repository/AbstractUserRepositoryImpl.java` |