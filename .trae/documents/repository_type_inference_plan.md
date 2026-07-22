# 仓库类型推断方案改进计划

## 一、当前实现分析

### 1.1 当前推断逻辑

在 [MultiRepositoryBeanPostProcessor.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/repository/MultiRepositoryBeanPostProcessor.java#L59-L77) 中的 `inferRepositoryType` 方法：

```java
private RepositoryType inferRepositoryType(Class<?> delegateClass) {
    // 1. 优先检查 @RepositoryType 注解
    RepositoryType annotation = AnnotationUtils.findAnnotation(delegateClass, RepositoryType.class);
    if (annotation != null) {
        return annotation.value();
    }
    
    // 2. 通过类名推断
    String className = delegateClass.getSimpleName().toUpperCase();
    if (className.contains("JPA")) {
        return RepositoryType.JPA;
    } else if (className.contains("MYBATIS")) {
        return RepositoryType.MYBATIS_PLUS;
    } else if (className.contains("MONGO")) {
        return RepositoryType.MONGODB;
    } else if (className.contains("ELASTICSEARCH")) {
        return RepositoryType.ELASTICSEARCH;
    }
    
    return RepositoryType.AUTO;
}
```

### 1.2 当前推断策略的问题

| 问题点 | 描述 | 示例 |
|-------|------|------|
| 类名依赖 | 强依赖类名包含特定关键词 | `CustomUserDelegate` 无法识别 |
| 继承链未利用 | 未利用父类继承关系 | `UserJpaDelegate extends JpaRepositoryDelegate` 应识别为 JPA |
| 注解标记受限 | 在基础 delegate 类上标记注解会影响所有子类 | 无法为不同实体定制类型 |

## 二、改进方案

### 2.1 新的推断策略（优先级从高到低）

```
1. 子类实现上的 @RepositoryType 注解
    ↓
2. 父类链上的 @RepositoryType 注解（递归查找）
    ↓
3. 父类类型匹配（JpaRepositoryDelegate → JPA 等）
    ↓
4. 类名关键词推断（兜底策略）
    ↓
5. AUTO（无法识别）
```

### 2.2 各 delegate 的父类关系

| 基础 Delegate | 父类/接口 | 仓库类型 |
|--------------|----------|---------|
| JpaRepositoryDelegate | implements RepositoryDelegate | JPA |
| MybatisPlusRepositoryDelegate | implements RepositoryDelegate | MYBATIS_PLUS |
| MongoRepositoryDelegate | implements RepositoryDelegate | MONGODB |
| ElasticsearchRepositoryDelegate | implements RepositoryDelegate | ELASTICSEARCH |
| InMemoryRepositoryDelegate | implements RepositoryDelegate | AUTO |

### 2.3 示例工程 delegate 继承关系

```
UserJpaDelegate → JpaRepositoryDelegate → RepositoryDelegate
UserMybatisPlusDelegate → MybatisPlusRepositoryDelegate → RepositoryDelegate
UserMongoDelegate → MongoRepositoryDelegate → RepositoryDelegate
UserEsDelegate → ElasticsearchRepositoryDelegate → RepositoryDelegate
```

## 三、实现步骤

### 3.1 修改 `MultiRepositoryBeanPostProcessor`

**文件**: `structure-infra-starter/src/main/java/cn/structure/infra/repository/MultiRepositoryBeanPostProcessor.java`

**修改内容**:
1. 增强 `inferRepositoryType` 方法，支持：
   - 递归查找父类链上的 `@RepositoryType` 注解
   - 通过父类类型匹配仓库类型
2. 添加 `isJpaDelegate`, `isMybatisPlusDelegate`, `isMongoDelegate`, `isElasticsearchDelegate` 辅助方法

### 3.2 在基础 Delegate 类上添加 `@RepositoryType` 注解

**注意**：由于各 starter 模块已依赖 `structure-infra-starter`，可以直接使用注解

| 文件 | 注解 |
|------|------|
| JpaRepositoryDelegate.java | `@RepositoryType(RepositoryType.JPA)` |
| MybatisPlusRepositoryDelegate.java | `@RepositoryType(RepositoryType.MYBATIS_PLUS)` |
| MongoRepositoryDelegate.java | `@RepositoryType(RepositoryType.MONGODB)` |
| ElasticsearchRepositoryDelegate.java | `@RepositoryType(RepositoryType.ELASTICSEARCH)` |

### 3.3 在示例工程的子类上标记注解（可选）

用户可以在自定义 delegate 子类上标记注解，覆盖父类的默认类型：

```java
// 默认继承父类的 JPA 类型
@Component
public class UserJpaDelegate extends JpaRepositoryDelegate<UserEntity, JpaUserPO, Long> 
        implements UserRepositoryDelegate { ... }

// 显式标记类型（可选，增强可读性）
@Component
@RepositoryType(RepositoryType.JPA)
public class UserJpaDelegate extends JpaRepositoryDelegate<UserEntity, JpaUserPO, Long> 
        implements UserRepositoryDelegate { ... }
```

### 3.4 创建多仓库示例工程

**新增模块**: `structure-infra-sample-multi`

**功能**:
- 同时引入 MyBatis Plus 和 MongoDB 依赖
- 配置多仓库支持
- 演示如何通过上下文切换仓库类型

## 四、文件清单

### 4.1 修改文件

| 文件 | 修改内容 |
|------|---------|
| `MultiRepositoryBeanPostProcessor.java` | 增强 `inferRepositoryType` 方法 |
| `JpaRepositoryDelegate.java` | 添加 `@RepositoryType(JPA)` 注解 |
| `MybatisPlusRepositoryDelegate.java` | 添加 `@RepositoryType(MYBATIS_PLUS)` 注解 |
| `MongoRepositoryDelegate.java` | 添加 `@RepositoryType(MONGODB)` 注解 |
| `ElasticsearchRepositoryDelegate.java` | 添加 `@RepositoryType(ELASTICSEARCH)` 注解 |

### 4.2 新增文件（示例工程）

| 文件 | 描述 |
|------|------|
| `structure-infra-sample-multi/pom.xml` | 多仓库示例工程依赖 |
| `MultiSampleApplication.java` | 启动类 |
| `application.yml` | 配置文件（启用多仓库） |
| `MultiRepositoryTest.java` | 测试用例 |

## 五、配置示例

### 5.1 YAML 配置

```yaml
structure:
  infra:
    multi-repository-enabled: true
    default-repository-type: MYBATIS_PLUS
```

### 5.2 业务侧使用

```java
// 方式1：通过上下文切换（编程式）
try (RepositoryTypeContext context = RepositoryTypeContext.use(RepositoryType.MONGODB)) {
    userRepository.save(user);
}

// 方式2：在自定义 delegate 上标记（声明式）
@Component
@RepositoryType(RepositoryType.ELASTICSEARCH)
public class SearchUserDelegate extends ElasticsearchRepositoryDelegate<UserEntity, UserPO, Long> 
        implements UserRepositoryDelegate { ... }

// 方式3：默认使用配置的仓库类型
userRepository.save(user);
```

## 六、风险与注意事项

| 风险点 | 应对措施 |
|-------|---------|
| 注解冲突 | 子类注解优先级高于父类 |
| 类型匹配失败 | 提供类名推断作为兜底 |
| 循环依赖 | 注解定义在 starter 模块，各模块已依赖 starter |
| 性能开销 | 缓存推断结果，避免重复计算 |