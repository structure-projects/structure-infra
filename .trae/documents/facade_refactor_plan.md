# 仓储门面重构计划

## 问题分析

### 当前架构问题
1. **编译错误**：`RepositoryBeanPostProcessor.java` 引用了不存在的 `ICommandDelegate` 接口
2. **层次结构混乱**：当前 `RepositoryFacade extends CqrsRepositoryFacade`，与用户期望相反
3. **多余门面类**：`BaseRepositoryFacade` 和 `SimpleRepositoryFacade` 不需要，应移除
4. **CQRS 泛型不足**：`CqrsRepositoryFacade` 只定义了一个代理泛型，需要两个

### 用户期望的架构
```
RepositoryFacade<T, ID>              // 基础门面（单代理模式）
    │
    └── CqrsRepositoryFacade<T, ID, D, RD>  // CQRS门面（读写分离，两个代理）
```

## 修改方案

### 1. 修复 RepositoryBeanPostProcessor 编译错误
- **文件**：`structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryBeanPostProcessor.java`
- **修改内容**：移除 `ICommandDelegate` 引用，只保留 `RepositoryDelegate` 和 `IQueryDelegate` 检查

### 2. 重构 RepositoryFacade（单代理模式）
- **文件**：`structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryFacade.java`
- **修改内容**：
  - 移除泛型参数 `D`
  - 添加 `protected RepositoryDelegate<T, ID> delegate`
  - 实现完整的 CRUD 方法（从 BaseRepositoryFacade 迁移）

### 3. 重构 CqrsRepositoryFacade（双代理模式）
- **文件**：`structure-infra-starter/src/main/java/cn/structure/infra/repository/CqrsRepositoryFacade.java`
- **修改内容**：
  - 继承 `RepositoryFacade<T, ID>`
  - 添加两个泛型参数：`D extends RepositoryDelegate<T, ID>`（基础代理）和 `RD extends IQueryDelegate<T, ID>`（读代理）
  - 添加 `protected D baseDelegate` 和 `protected RD readDelegate`
  - 重写读方法，实现回退机制

### 4. 删除多余门面类
- **删除文件**：
  - `structure-infra-starter/src/main/java/cn/structure/infra/repository/BaseRepositoryFacade.java`
  - `structure-infra-starter/src/main/java/cn/structure/infra/repository/SimpleRepositoryFacade.java`

### 5. 更新示例代码

#### 5.1 更新 AbstractUserRepositoryImpl（单代理）
- **文件**：`structure-infra-sample/structure-infra-sample-core/src/main/java/cn/structure/infra/sample/infra/repository/AbstractUserRepositoryImpl.java`
- **修改内容**：改为继承 `RepositoryFacade<UserEntity, Long>`

#### 5.2 更新 UserCqrsRepository（双代理）
- **文件**：`structure-infra-sample/structure-infra-sample-cqrs/src/main/java/cn/structure/infra/sample/cqrs/infra/repositoory/UserCqrsRepository.java`
- **修改内容**：改为继承 `CqrsRepositoryFacade<UserEntity, Long, UserWriteDelegate, UserReadDelegate>`

### 6. 更新 RepositoryBeanPostProcessor
- **文件**：`structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryBeanPostProcessor.java`
- **修改内容**：
  - 支持单代理模式的 `RepositoryFacade`（注入 `delegate`）
  - 支持双代理模式的 `CqrsRepositoryFacade`（注入 `baseDelegate` 和 `readDelegate`）

### 7. 修复测试代码
- **文件**：`structure-infra-sample/structure-infra-sample-cqrs/src/test/java/cn/structure/infra/sample/cqrs/UserCqrsRepositoryTest.java`
- **修改内容**：调整泛型类型和方法调用

## 依赖关系

```
RepositoryFacade (单代理)
    │
    ├── AbstractUserRepositoryImpl
    │       ├── UserRepositoryImpl (MyBatis)
    │       ├── UserJpaRepositoryImpl
    │       ├── UserMongoRepositoryImpl
    │       └── UserElasticsearchRepositoryImpl
    │
    └── CqrsRepositoryFacade (双代理)
            └── UserCqrsRepository
```

## 风险提示

1. **泛型兼容性**：修改泛型参数可能导致编译错误，需要全面检查所有继承 RepositoryFacade 的类
2. **测试影响**：测试代码中使用的 `getBaseDelegate()` 和 `getReadDelegate()` 方法名可能需要调整
3. **Bean 注入**：RepositoryBeanPostProcessor 需要正确识别单代理和双代理模式的门面

## 验证步骤

1. 编译整个项目，检查是否有编译错误
2. 运行 MyBatis、JPA、MongoDB 模块测试（单代理模式）
3. 运行 CQRS 模块测试（双代理模式）
4. 验证读操作回退机制是否正常工作
