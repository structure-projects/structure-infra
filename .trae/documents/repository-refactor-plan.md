# 仓储架构重构修正方案

## 一、现状分析

### 1.1 当前架构问题

| 问题点 | 描述 | 影响范围 |
|--------|------|----------|
| 注解冗余 | `@Repository` 和 `@DelegateFor` 注解与泛型定义重复 | 所有示例模块 |
| PO类型冗余 | PO类型既在注解中定义，又在泛型中定义 | Delegate实现类 |
| 注释缺失 | 之前修复过程中部分类/方法注释被移除 | 多处文件 |
| Facade设计 | RepositoryFacade承担了过多职责，缺乏分层 | 框架核心 |

### 1.2 依赖关系分析

```
┌─────────────────────────────────────────────────────────────────────┐
│                        领域层 (Domain)                              │
│  UserRepository (接口) ──> UserEntity (领域实体)                    │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        仓储门面层 (Facade)                           │
│  AbstractUserRepositoryImpl ──> RepositoryFacade<T, ID, D>         │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        委托实现层 (Delegate)                         │
│  UserMybatisPlusDelegate ──> MybatisPlusRepositoryDelegate<E,P,ID> │
│  UserJpaDelegate ──> JpaRepositoryDelegate<E,P,ID>                 │
│  UserMongoDelegate ──> MongoRepositoryDelegate<E,P,ID>             │
│  UserEsDelegate ──> ElasticsearchRepositoryDelegate<E,P,ID>        │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.3 现有注解使用情况

| 注解 | 用途 | 是否可移除 |
|------|------|-----------|
| `@Repository` | 标记仓储实现、指定类型和实体 | 可移除，由泛型推断 |
| `@DelegateFor` | 标记委托实现、指定名称和PO类型 | 可移除，由命名约定和泛型推断 |
| `@Component` | Spring组件注册 | **保留** |

---

## 二、修正方案

### 2.1 方案一：移除冗余注解（推荐）

**核心思想**：利用泛型和命名约定替代注解配置，让Spring自动管理依赖注入。

#### 2.1.1 命名约定

| 组件类型 | 命名模式 | 示例 |
|----------|----------|------|
| Repository接口 | `{Entity}Repository` | `UserRepository` |
| Repository实现 | `{Entity}{Tech}RepositoryImpl` | `UserJpaRepositoryImpl` |
| Delegate接口 | `{Entity}RepositoryDelegate` | `UserRepositoryDelegate` |
| Delegate实现 | `{Entity}{Tech}Delegate` | `UserMybatisPlusDelegate` |

#### 2.1.2 泛型解析策略

BeanPostProcessor通过反射解析泛型参数获取：
- Entity类型：从 `RepositoryDelegate<E, ID>` 获取
- PO类型：从 `MybatisPlusRepositoryDelegate<E, P, ID>` 获取
- ID类型：从泛型参数获取

### 2.2 方案二：Facade分层设计

**核心思想**：将RepositoryFacade拆分为基础门面和具体门面，支持独立分离的门面实现。

#### 2.2.1 分层结构

```
BaseRepositoryFacade<T, ID>           // 基础CRUD能力
    │
    ├── SimpleRepositoryFacade<T, ID> // 简化门面（无CQRS）
    │
    └── CqrsRepositoryFacade<T, ID>  // CQRS门面（读写分离）
            │
            └── RepositoryFacade<T, ID, D> // 当前实现
```

#### 2.2.2 职责划分

| 类 | 职责 |
|----|------|
| `BaseRepositoryFacade` | 基础CRUD接口实现、实体管理 |
| `SimpleRepositoryFacade` | 单代理模式（无CQRS） |
| `CqrsRepositoryFacade` | CQRS模式（读写分离）、回退机制 |

---

## 三、实施步骤

### 3.1 步骤一：增强BeanPostProcessor泛型解析能力

**目标**：让BeanPostProcessor能够从泛型中解析Entity和PO类型，不再依赖注解。

**修改文件**：
- `structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryBeanPostProcessor.java`
  - 增强泛型解析逻辑，支持从继承链中提取Entity和PO类型
  - 根据命名约定自动匹配Delegate和Facade

### 3.2 步骤二：移除示例工程中的冗余注解

**目标**：移除 `@Repository` 和 `@DelegateFor` 注解，验证泛型解析是否正常工作。

**修改文件**：
- `structure-infra-sample/structure-infra-sample-jpa/src/main/java/cn/structure/infra/sample/infra/repository/UserJpaRepositoryImpl.java`
  - 移除 `@Repository` 注解
  - 保留 `@Component` 和类注释

- `structure-infra-sample/structure-infra-sample-jpa/src/main/java/cn/structure/infra/sample/infra/repository/jpa/UserJpaDelegate.java`
  - 移除 `@DelegateFor` 注解
  - 保留 `@Component` 和类注释

- `structure-infra-sample/structure-infra-sample-mongodb/src/main/java/cn/structure/infra/sample/infra/repository/UserMongoRepositoryImpl.java`
  - 移除 `@Repository` 注解

- `structure-infra-sample/structure-infra-sample-mongodb/src/main/java/cn/structure/infra/sample/infra/repository/mongodb/UserMongoDelegate.java`
  - 移除 `@DelegateFor` 注解

- `structure-infra-sample/structure-infra-sample-elasticsearch/src/main/java/cn/structure/infra/sample/infra/repository/UserElasticsearchRepositoryImpl.java`
  - 移除 `@Repository` 注解

- `structure-infra-sample/structure-infra-sample-elasticsearch/src/main/java/cn/structure/infra/sample/infra/repository/elasticsearch/UserEsDelegate.java`
  - 移除 `@DelegateFor` 注解

- `structure-infra-sample/structure-infra-sample-mybatis/src/main/java/cn/structure/infra/sample/infra/repository/mybatis/UserMybatisPlusDelegate.java`
  - 移除 `@DelegateFor` 注解

### 3.3 步骤三：补充缺失的注释

**目标**：恢复之前修复过程中被移除的类注释、方法注释和行注释。

**修改文件**：
- `structure-infra-mybatis-plus-starter/src/main/java/cn/structure/infra/mybatis/plus/repository/MybatisPlusDelegateBeanPostProcessor.java`
  - 恢复 `findMapperByPoClass` 方法的注释

- `structure-infra-sample/structure-infra-sample-jpa/src/main/java/cn/structure/infra/sample/infra/repository/jpa/UserJpaDelegate.java`
  - 恢复类注释和方法注释

- `structure-infra-sample/structure-infra-sample-mongodb/src/main/java/cn/structure/infra/sample/infra/repository/mongodb/UserMongoDelegate.java`
  - 恢复类注释和方法注释

- `structure-infra-sample/structure-infra-sample-elasticsearch/src/main/java/cn/structure/infra/sample/infra/repository/elasticsearch/UserEsDelegate.java`
  - 恢复类注释和方法注释

### 3.4 步骤四：Facade分层设计（可选）

**目标**：创建分层的Facade结构，支持独立分离的门面实现。

**新增文件**：
- `structure-infra-starter/src/main/java/cn/structure/infra/repository/BaseRepositoryFacade.java`
  - 基础CRUD能力，不包含CQRS逻辑

- `structure-infra-starter/src/main/java/cn/structure/infra/repository/SimpleRepositoryFacade.java`
  - 简化门面，单代理模式

**修改文件**：
- `structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryFacade.java`
  - 继承 `CqrsRepositoryFacade`
  - 专注于CQRS模式

---

## 四、风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 泛型解析失败 | 无法正确识别Entity/PO类型 | 保留注解作为备用方案 |
| 命名约定冲突 | 多个Delegate匹配同一个Facade | 增加优先级配置 |
| BeanPostProcessor顺序问题 | 依赖未就绪导致注入失败 | 使用 `@DependsOn` 或 `@Order` |
| 兼容性问题 | 现有代码无法编译 | 逐步迁移，保留兼容层 |

---

## 五、验证计划

### 5.1 编译验证
```bash
mvn clean compile
```

### 5.2 单元测试验证
```bash
mvn test -pl structure-infra-sample/structure-infra-sample-mybatis
mvn test -pl structure-infra-sample/structure-infra-sample-jpa
mvn test -pl structure-infra-sample/structure-infra-sample-mongodb
mvn test -pl structure-infra-sample/structure-infra-sample-elasticsearch
```

### 5.3 集成测试验证
```bash
mvn test -pl structure-infra-sample/structure-infra-sample-cqrs
```

---

## 六、预期效果

1. **代码简化**：移除冗余注解，减少样板代码
2. **类型安全**：泛型推断确保编译时类型检查
3. **Spring原生**：利用Spring依赖注入机制，减少自定义配置
4. **可扩展性**：分层Facade设计支持多种使用模式
5. **注释完整**：恢复所有必要的文档注释
