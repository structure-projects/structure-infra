# CQRS 代理注入问题修复方案

## 问题分析

### 错误描述

Spring Boot 启动时抛出 `NoUniqueBeanDefinitionException`：

```
Field delegate in cn.structure.infra.repository.RepositoryFacade required a single bean, but 3 were found:
    - userReadDelegate
    - userWriteDelegate  
    - userMybatisPlusDelegate
```

### 根本原因

`RepositoryFacade` 的 `delegate` 字段使用 `@Autowired(required = false)` 注入，但当存在多个匹配类型的 Bean 时，Spring 无法确定注入哪一个。`required = false` 仅在没有匹配 Bean 时生效，无法解决多 Bean 歧义问题。

### 当前状态

| 组件                                  | 注解                             | 问题          |
| ----------------------------------- | ------------------------------ | ----------- |
| `UserReadDelegate`                  | `@Component` + `@ReadDelegate` | 已标记读代理      |
| `UserWriteDelegate`                 | `@Component`                   | 未标记写代理      |
| `RepositoryFacade.delegate`         | `@Autowired(required = false)` | 无限定符，无法区分读写 |
| `CqrsRepositoryFacade.readDelegate` | `@Autowired(required = false)` | 无限定符，无法精准注入 |

***

## 解决方案

### 设计思路

通过**注解标记 + 自定义限定符**机制，让 Spring 能够根据代理类型（读/写）精准注入：

1. **新增** **`@WriteDelegate`** **注解**：标记写代理/基础代理
2. **修改** **`RepositoryFacade`**：`delegate` 字段注入写代理（排除 `@ReadDelegate`）
3. **修改** **`CqrsRepositoryFacade`**：`readDelegate` 字段注入读代理（仅 `@ReadDelegate`）
4. **保留** **`@ReadDelegate`** **注解**：保持现有功能不变

### 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Context                               │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐ │
│  │ @Component       │  │ @Component       │  │ @Component    │ │
│  │ @ReadDelegate    │  │ @WriteDelegate   │  │ (无标记)      │ │
│  │ UserReadDelegate │  │ UserWriteDelegate│  │ Others...     │ │
│  └────────┬─────────┘  └────────┬─────────┘  └───────────────┘ │
│           │                     │                               │
│           ▼                     ▼                               │
│  ┌───────────────────────────────────────────────┐             │
│  │         RepositoryBeanPostProcessor           │             │
│  │   - 根据注解筛选 Bean，注入到 Facade 对应字段   │             │
│  └───────────────────────────────────────────────┘             │
└─────────────────────────────────────────────────────────────────┘
```

***

## 修改步骤

### 步骤 1：新增 `@WriteDelegate` 注解

**文件位置**：`structure-infra-starter/src/main/java/cn/structure/infra/annotations/WriteDelegate.java`

**内容**：

```java
@Qualifier
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WriteDelegate {
}
```

***

### 步骤 2：修改 `RepositoryFacade` 注入逻辑

**文件位置**：`structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryFacade.java`

**修改内容**：

* 将 `@Autowired(required = false)` 替换为自定义注入逻辑

* 通过 `@Qualifier` 或 BeanPostProcessor 确保只注入写代理（无 `@ReadDelegate` 标记的 Bean）

***

### 步骤 3：修改 `CqrsRepositoryFacade` 注入逻辑

**文件位置**：`structure-infra-starter/src/main/java/cn/structure/infra/repository/CqrsRepositoryFacade.java`

**修改内容**：

* 将 `readDelegate` 字段添加 `@ReadDelegate` 限定符

* 确保 `delegate` 字段（继承自父类）注入写代理

***

### 步骤 4：创建 `RepositoryBeanPostProcessor`

**文件位置**：`structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryBeanPostProcessor.java`

**功能**：

* 遍历所有 `RepositoryFacade` 和 `CqrsRepositoryFacade` 实例

* 根据泛型类型和注解标记，精准注入对应的 delegate

* 优先注入标记了 `@WriteDelegate` 的 Bean 作为 baseDelegate

* 优先注入标记了 `@ReadDelegate` 的 Bean 作为 readDelegate

***

### 步骤 5：更新 `AutoRepositoryConfiguration`

**文件位置**：`structure-infra-starter/src/main/java/cn/structure/infra/configuration/AutoRepositoryConfiguration.java`

**修改内容**：

* 注册 `RepositoryBeanPostProcessor`

***

### 步骤 6：更新 `UserWriteDelegate` 示例

**文件位置**：`structure-infra-sample/structure-infra-sample-cqrs/src/main/java/cn/structure/infra/sample/cqrs/infra/delegate/write/UserWriteDelegate.java`

**修改内容**：

* 添加 `@WriteDelegate` 注解

***

## 关键代码设计

### 1. RepositoryBeanPostProcessor 核心逻辑

```java
public class RepositoryBeanPostProcessor implements BeanPostProcessor {
    
    @Autowired
    private ListableBeanFactory beanFactory;
    
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof CqrsRepositoryFacade) {
            processCqrsFacade((CqrsRepositoryFacade<?, ?, ?, ?>) bean);
        } else if (bean instanceof RepositoryFacade) {
            processRepositoryFacade((RepositoryFacade<?, ?, ?>) bean);
        }
        return bean;
    }
    
    private void processCqrsFacade(CqrsRepositoryFacade<?, ?, ?, ?> facade) {
        // 通过泛型获取代理类型
        Type[] types = GenericTypeResolver.resolveTypeArguments(
            facade.getClass(), CqrsRepositoryFacade.class);
        
        // 注入写代理（优先 @WriteDelegate，其次无 @ReadDelegate 的 Bean）
        Object writeDelegate = findDelegate((Class<?>) types[2], true);
        // 注入读代理（优先 @ReadDelegate 标记的 Bean）
        Object readDelegate = findDelegate((Class<?>) types[3], false);
        
        // 通过反射设置字段
    }
}
```

### 2. delegate 查找策略

| 场景     | 优先级                     | 说明       |
| ------ | ----------------------- | -------- |
| 写代理注入  | 1. `@WriteDelegate` 标记  | 显式标记的写代理 |
| <br /> | 2. 无 `@ReadDelegate` 标记 | 默认作为写代理  |
| <br /> | 3. 任意匹配类型               | 兜底策略     |
| 读代理注入  | 1. `@ReadDelegate` 标记   | 显式标记的读代理 |
| <br /> | 2. 任意匹配类型               | 兜底策略     |

***

## 风险与注意事项

### 风险点

1. **泛型类型解析失败**：如果子类未正确声明泛型参数，可能导致注入失败
2. **多 Bean 冲突**：如果同一类型同时存在多个 `@WriteDelegate` 或 `@ReadDelegate`，仍会冲突
3. **性能影响**：BeanPostProcessor 在每个 Bean 创建时都会执行

### 注意事项

1. 确保所有 Delegate 实现类都标记了正确的注解
2. 对于非 CQRS 场景（单代理），不需要标记 `@WriteDelegate`，框架会自动选择无 `@ReadDelegate` 标记的 Bean
3. 保留 `required = false` 的容错机制，确保非 CQRS 场景也能正常运行

***

## 测试验证

### 验证步骤

1. **编译测试**：`mvn clean compile`
2. **单元测试**：`mvn test -pl structure-infra-sample/structure-infra-sample-cqrs`
3. **集成测试**：启动 CQRS 示例应用，验证代理正确注入

### 预期结果

* `UserCqrsRepository` 的 `delegate` 字段注入 `UserWriteDelegate`

* `UserCqrsRepository` 的 `readDelegate` 字段注入 `UserReadDelegate`

* 单代理模式的 `UserRepositoryImpl` 正常工作，不受影响

***

## 兼容性说明

| 场景           | 兼容 | 说明                                         |
| ------------ | -- | ------------------------------------------ |
| 单代理模式（现有代码）  | 是  | 无需修改，框架自动选择无 `@ReadDelegate` 的 Bean        |
| CQRS 模式（修改后） | 是  | 需要添加 `@WriteDelegate` 和 `@ReadDelegate` 注解 |
| 混合模式（多个模块）   | 是  | 通过注解标记区分不同用途的代理                            |

