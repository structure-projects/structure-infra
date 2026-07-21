# 三种仓库场景创建流程验证计划

## 一、设计原则

### 核心原则

1. **普通单仓库场景：Spring 默认管理，不接管**

   * 完全由 Spring 容器负责依赖注入

   * 支持构造函数注入、`@Autowired` 字段注入等原生方式

   * `RepositoryBeanPostProcessor` 不处理普通 `RepositoryFacade`

2. **CQRS 场景：RepositoryBeanPostProcessor 接管增强**

   * 有明确注解标记（`@WriteDelegate` / `@ReadDelegate`）

   * 接管但不破坏原生注入：base delegate 仍可通过构造函数注入

   * BeanPostProcessor 负责注入 readDelegate（额外的依赖）

3. **多仓库场景：MultiRepositoryBeanPostProcessor 接管增强**

   * 有明确类型标记（`@RepositoryTypeAnnotation` 或类型推断）

   * 接管但不破坏原生注入：default delegate 仍可通过构造函数注入

   * BeanPostProcessor 负责注册多个 delegates 到 Map 中

### 判断标准：是否接管？

| 场景    | 有注解标记？                             | 注入方式                                    | 谁负责                                       |
| ----- | ---------------------------------- | --------------------------------------- | ----------------------------------------- |
| 普通单仓库 | ❌ 无                                | 构造函数 / @Autowired                       | Spring 原生                                 |
| CQRS  | ✅ @WriteDelegate / @ReadDelegate   | 构造函数(base) + BeanPostProcessor(read)    | Spring + RepositoryBeanPostProcessor      |
| 多仓库   | ✅ @RepositoryTypeAnnotation / 类型推断 | 构造函数(default) + BeanPostProcessor(注册多个) | Spring + MultiRepositoryBeanPostProcessor |

***

## 二、现状分析

### 2.1 CQRS 场景

**基类层次：**

```
RepositoryFacade<T, ID, D>
    ↑
CqrsRepositoryFacade<T, ID, D, RD>  // 增加 readDelegate 字段
```

**当前注入机制：**

* 处理器：[RepositoryBeanPostProcessor](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryBeanPostProcessor.java)

* 时机：`postProcessAfterInitialization()` 中检测 `CqrsRepositoryFacade` 实例

* 方式：通过反射注入 `delegate`（写）和 `readDelegate`（读）字段

* 注解标记：

  * `@WriteDelegate`：标记写委托类

  * `@ReadDelegate`：标记读委托类

* 匹配逻辑：

  * 写委托：优先 `@WriteDelegate` 标记 → 无 `@ReadDelegate` 标记 → 第一个匹配

  * 读委托：优先 `@ReadDelegate` 标记 → 第一个匹配

**当前问题：**

* delegate 完全通过反射注入，绕过了 Spring 原生注入

* 不支持构造函数注入方式

* 如果用户在子类中通过构造函数注入 delegate，会被 BeanPostProcessor 覆盖

**示例：**

* Facade: [UserCqrsRepository](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-cqrs/src/main/java/cn/structure/infra/sample/cqrs/infra/repository/UserCqrsRepository.java)

  * `extends CqrsRepositoryFacade<UserEntity, Long, UserWriteDelegate, UserReadDelegate>`

* 写委托: [UserWriteDelegate](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-cqrs/src/main/java/cn/structure/infra/sample/cqrs/infra/delegate/write/UserWriteDelegate.java) - `@WriteDelegate`

* 读委托: [UserReadDelegate](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-cqrs/src/main/java/cn/structure/infra/sample/cqrs/infra/delegate/read/UserReadDelegate.java) - `@ReadDelegate`

***

### 2.2 多仓库场景

**基类层次：**

```
RepositoryFacade<T, ID, D>
    ↑
MultiRepositoryFacade<T, ID, D>  // 维护 delegates Map，支持动态切换
```

**当前注入机制：**

* 处理器：[MultiRepositoryBeanPostProcessor](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/repository/MultiRepositoryBeanPostProcessor.java)

* 时机：

  1. `postProcessAfterInitialization()`：收集所有 `RepositoryDelegate`，注册到 `delegateRegistry`
  2. `afterSingletonsInstantiated()`：将注册的 delegate 注入到所有 `MultiRepositoryFacade`

* 方式：通过 `facade.registerDelegate(type, delegate)` 注册到 delegates Map

* 类型推断（RepositoryType）：

  1. `@RepositoryTypeAnnotation` 注解标记
  2. 父类/接口上的 `@RepositoryTypeAnnotation`
  3. 类名匹配（JPA/MYBATIS/MONGO/ELASTICSEARCH）
  4. 父类名匹配（JpaRepositoryDelegate/MybatisPlusRepositoryDelegate 等）

**当前问题：**

* delegate 完全通过反射注入/注册，绕过了 Spring 原生注入

* 不支持构造函数注入方式

* defaultDelegate 的设置逻辑在 BeanPostProcessor 中硬编码

**示例：**

* Facade: [AbstractUserRepositoryImpl](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-multi/src/main/java/cn/structure/infra/sample/multi/infra/repository/AbstractUserRepositoryImpl.java)

  * `extends MultiRepositoryFacade<UserEntity, Long, UserRepositoryDelegate>`

* 委托实现：

  * [UserMybatisPlusDelegate](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-multi/src/main/java/cn/structure/infra/sample/multi/infra/repository/mybatis/UserMybatisPlusDelegate.java)

  * [UserMongoDelegate](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-multi/src/main/java/cn/structure/infra/sample/multi/infra/repository/mongodb/UserMongoDelegate.java)

***

### 2.3 普通单仓库场景

**基类层次：**

```
RepositoryFacade<T, ID, D>  // 单个 delegate 字段
```

**当前注入机制：**

* 处理器：[RepositoryBeanPostProcessor](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryBeanPostProcessor.java)

* 时机：`afterSingletonsInstantiated()` 中遍历所有 `RepositoryFacade` bean

* 方式：通过反射注入 `delegate` 字段

* 匹配逻辑：按泛型类型 `D` 查找 bean，使用 `findWriteDelegate()` 逻辑

* **无专门注解标记 → 不应该接管！**

**当前问题：**

* 普通单仓库没有注解标记，但仍然被 `RepositoryBeanPostProcessor` 接管

* 绕过了 Spring 原生注入机制

* 用户无法使用构造函数注入等 Spring 标准方式

**示例：**

* Facade: [UserJpaRepositoryImpl](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-jpa/src/main/java/cn/structure/infra/sample/infra/repository/UserJpaRepositoryImpl.java)

  * `extends AbstractUserRepositoryImpl extends RepositoryFacade<UserEntity, Long, UserRepositoryDelegate>`

* 委托: [UserJpaDelegate](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-jpa/src/main/java/cn/structure/infra/sample/infra/repository/jpa/UserJpaDelegate.java)

***

## 三、修复方案设计

### 3.1 总体架构

```
RepositoryFacade (普通单仓库)
    │  完全由 Spring 管理：构造函数注入 / @Autowired
    │  RepositoryBeanPostProcessor 不处理
    │
    ├─ CqrsRepositoryFacade (CQRS)
    │      base delegate: Spring 构造函数注入
    │      readDelegate: RepositoryBeanPostProcessor 注入（增强）
    │
    └─ MultiRepositoryFacade (多仓库)
           default delegate: Spring 构造函数注入
           delegates Map: MultiRepositoryBeanPostProcessor 注册（增强）
```

### 3.2 RepositoryFacade 改造（基础层）

**目标：** 支持 Spring 原生注入方式（构造函数注入优先）

**修改内容：**

```java
public class RepositoryFacade<T, ID, D extends RepositoryDelegate<T, ID>> implements ICrudRepository<T, ID> {

    protected final D delegate;  // final，通过构造函数注入

    protected Class<T> entityClass;

    // 构造函数注入：支持 Spring 原生方式
    public RepositoryFacade(D delegate) {
        this.delegate = delegate;
    }

    // 保留无参构造函数，兼容 BeanPostProcessor 反射注入（过渡期）
    protected RepositoryFacade() {
        this.delegate = null;
    }

    // ... 其他方法使用 this.delegate
}
```

**关键点：**

1. `delegate` 字段改为 `final`，通过构造函数注入
2. 提供带 `D delegate` 参数的构造函数
3. 保留无参构造函数（protected），供 BeanPostProcessor 反射场景兼容
4. 子类如果使用构造函数注入，Spring 自动按类型匹配 delegate

### 3.3 CqrsRepositoryFacade 改造（CQRS 增强）

**目标：** base delegate 走 Spring 注入，readDelegate 由 BeanPostProcessor 增强

**修改内容：**

```java
public class CqrsRepositoryFacade<T, ID, D extends RepositoryDelegate<T, ID>, RD extends IQueryDelegate<T, ID>>
        extends RepositoryFacade<T, ID, D> {

    protected RD readDelegate;  // 非 final，由 BeanPostProcessor 注入

    // 构造函数注入 base delegate
    public CqrsRepositoryFacade(D delegate) {
        super(delegate);
    }

    // 保留无参构造函数（过渡期兼容）
    protected CqrsRepositoryFacade() {
        super();
    }

    // ... 读操作使用 readDelegate，失败回退 super.delegate
}
```

**RepositoryBeanPostProcessor 改造：**

* 移除 `processRepositoryFacade()` 方法（普通单仓库不再处理）

* 保留 `processCqrsFacade()` 方法，但只注入 `readDelegate`

* `delegate` 由 Spring 构造函数注入，BeanPostProcessor 不覆盖

* `afterSingletonsInstantiated()` 中移除对普通 `RepositoryFacade` 的遍历

**注入流程：**

1. Spring 实例化 CqrsRepositoryFacade 子类 → 构造函数注入 base delegate
2. `postProcessAfterInitialization()` → RepositoryBeanPostProcessor 注入 readDelegate
3. 结果：base delegate 由 Spring 管理，readDelegate 由 BeanPostProcessor 增强

### 3.4 MultiRepositoryFacade 改造（多仓库增强）

**目标：** default delegate 走 Spring 注入（复用父类 delegate 字段），其他 delegates 由 BeanPostProcessor 注册

**修正要点：**

1. **移除冗余的** **`defaultDelegate`** **字段** → 复用父类 `RepositoryFacade` 的 `delegate` 字段作为 default delegate
2. 构造函数注入 default delegate → 传给 `super(defaultDelegate)`
3. `getCurrentDelegate()` 回退时使用 `getDelegate()`（父类方法）
4. delegates Map 存储所有可用的 delegate，按类型索引

**修改内容：**

```java
public class MultiRepositoryFacade<T, ID, D extends RepositoryDelegate<T, ID>> extends RepositoryFacade<T, ID, D> {

    // 存储所有可用的 delegate，按 RepositoryType 索引
    private final Map<RepositoryType, D> delegates = new HashMap<>();

    // 默认使用的仓库类型（AUTO 表示自动选择）
    @Getter
    @Setter
    private RepositoryType defaultType = RepositoryType.AUTO;

    // 构造函数注入 default delegate，传给父类
    // 父类的 delegate 字段即为 default delegate
    public MultiRepositoryFacade(D defaultDelegate) {
        super(defaultDelegate);
    }

    // 保留无参构造函数（过渡期兼容，配合 BeanPostProcessor 反射注入）
    protected MultiRepositoryFacade() {
        super();
    }

    /**
     * 获取当前使用的 delegate
     * 优先级：上下文类型 > 默认类型 > 父类 delegate(default) > delegates Map 第一个
     */
    public D getCurrentDelegate() {
        // 1. 优先使用线程上下文中指定的类型
        RepositoryType contextType = RepositoryTypeContext.get();
        if (contextType != null && delegates.containsKey(contextType)) {
            log.debug("Using context repository type: {}", contextType);
            return delegates.get(contextType);
        }

        // 2. 其次使用配置的 defaultType
        if (defaultType != RepositoryType.AUTO && delegates.containsKey(defaultType)) {
            log.debug("Using default repository type: {}", defaultType);
            return delegates.get(defaultType);
        }

        // 3. 回退到父类的 delegate（构造函数注入的 default delegate）
        if (getDelegate() != null) {
            log.debug("Using default delegate from constructor injection");
            return getDelegate();
        }

        // 4. 最后尝试 delegates Map 中的第一个
        if (!delegates.isEmpty()) {
            D firstDelegate = delegates.values().iterator().next();
            log.debug("Using first available delegate: {}", firstDelegate.getClass().getSimpleName());
            return firstDelegate;
        }

        throw new IllegalStateException("No repository delegate available");
    }

    /**
     * 注册指定类型的 delegate
     * 由 MultiRepositoryBeanPostProcessor 调用
     */
    public void registerDelegate(RepositoryType type, D delegate) {
        delegates.put(type, delegate);
        log.info("Registered repository delegate: type={}, delegate={}", type, delegate.getClass().getSimpleName());
    }

    /**
     * 检查是否有指定类型的 delegate
     */
    public boolean hasDelegate(RepositoryType type) {
        return delegates.containsKey(type);
    }

    /**
     * 获取所有已注册的类型
     */
    public Set<RepositoryType> getRegisteredTypes() {
        return Collections.unmodifiableSet(delegates.keySet());
    }

    // ... CRUD 方法全部重写，使用 getCurrentDelegate()
    // （当前代码已经重写了，保持不变）
}
```

**关键设计说明：**

| 字段              | 来源                     | 作用                   |
| --------------- | ---------------------- | -------------------- |
| `delegate`（父类）  | 构造函数注入（Spring）         | default delegate，兜底用 |
| `delegates` Map | BeanPostProcessor 注册   | 所有可用的 delegate，按类型切换 |
| `defaultType`   | 配置 / BeanPostProcessor | 默认使用的仓库类型            |

**为什么不直接用 delegates Map 存 default？**

* 构造函数注入的 delegate 类型未知（BeanPostProcessor 还没运行）

* 父类 delegate 字段是 RepositoryFacade 的标准，保持一致性

* 双重保障：即使 delegates Map 为空，也有 default delegate 可用

**MultiRepositoryBeanPostProcessor 改造：**

* 职责更清晰：只负责收集 delegate 并注册到 MultiRepositoryFacade

* 不负责设置 defaultDelegate（default 由 Spring 构造函数注入，复用父类 delegate）

* `injectDelegatesToMultiFacades()` 中移除 `facade.setDefaultDelegate()` 的调用

* 可选：如果 defaultType 配置了，且对应 delegate 存在，可以设置 defaultType

**注入流程：**

1. Spring 实例化 MultiRepositoryFacade 子类 → 构造函数注入 default delegate → 传给父类
2. `postProcessAfterInitialization()` → 收集所有 RepositoryDelegate，注册到 delegateRegistry
3. `afterSingletonsInstantiated()` → 将所有 delegate 注册到 MultiRepositoryFacade 的 delegates Map
4. 结果：default delegate 由 Spring 管理（父类 delegate），其他 delegates 由 BeanPostProcessor 增强注册

### 3.5 RepositoryBeanPostProcessor 改造（职责收缩）

**修改前：**

* `postProcessAfterInitialization()`：处理 CqrsRepositoryFacade（注入 delegate + readDelegate）

* `afterSingletonsInstantiated()`：处理所有 RepositoryFacade（注入 delegate）

**修改后：**

* `postProcessAfterInitialization()`：处理 CqrsRepositoryFacade（**只注入 readDelegate**）

* `afterSingletonsInstantiated()`：**移除**，不再处理普通 RepositoryFacade

* 普通 RepositoryFacade 完全由 Spring 管理

**代码变更示意：**

```java
public class RepositoryBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    private DefaultListableBeanFactory beanFactory;
    private InfraProperties infraProperties;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (Boolean.TRUE.equals(infraProperties.getMultiRepositoryEnabled())) {
            return bean;
        }
        // 只处理 CQRS 场景，且只注入 readDelegate
        if (bean instanceof CqrsRepositoryFacade) {
            processCqrsReadDelegate((CqrsRepositoryFacade<?, ?, ?, ?>) bean, beanName);
        }
        return bean;
    }

    // 移除 afterSingletonsInstantiated() 方法
    // 移除 processRepositoryFacade() 方法

    private void processCqrsReadDelegate(CqrsRepositoryFacade<?, ?, ?, ?> facade, String beanName) {
        // 只注入 readDelegate，delegate 由 Spring 构造函数注入
        Type[] typeArgs = resolveTypeArguments(facade.getClass(), CqrsRepositoryFacade.class);
        if (typeArgs == null || typeArgs.length < 4) {
            log.warn("Cannot resolve generic types for CqrsRepositoryFacade: {}", beanName);
            return;
        }

        Class<?> readDelegateType = getRawType(typeArgs[3]);
        if (readDelegateType != null) {
            Object readDelegate = findReadDelegate(readDelegateType);
            if (readDelegate != null) {
                setDelegate(facade, "readDelegate", readDelegate);
                log.info("Injected read delegate [{}] to CqrsRepositoryFacade [{}]",
                        readDelegate.getClass().getSimpleName(), beanName);
            }
        }
    }

    // ... 其他辅助方法保留
}
```

### 3.6 子类实现方式（用户代码）

**普通单仓库（Spring 原生方式）：**

```java
@Component("userRepository")
public class UserJpaRepositoryImpl extends AbstractUserRepositoryImpl {

    // 构造函数注入，Spring 自动按类型匹配 UserJpaDelegate
    public UserJpaRepositoryImpl(UserJpaDelegate delegate) {
        super(delegate);
    }
}
```

**CQRS 场景：**

```java
@Component("userCqrsRepository")
public class UserCqrsRepository extends CqrsRepositoryFacade<UserEntity, Long, UserWriteDelegate, UserReadDelegate>
        implements UserRepository {

    // 构造函数注入 base delegate（写代理）
    public UserCqrsRepository(UserWriteDelegate writeDelegate) {
        super(writeDelegate);
    }

    // readDelegate 由 RepositoryBeanPostProcessor 自动注入
}
```

**多仓库场景：**

```java
@Component("userRepository")
public class UserRepositoryImpl extends MultiRepositoryFacade<UserEntity, Long, UserReadDelegate> implements UserRepository{

    // 构造函数注入 default delegate
    public UserRepositoryImpl(UserMybatisPlusDelegate defaultDelegate) {
        super(defaultDelegate);
    }

    // 其他 delegate 由 MultiRepositoryBeanPostProcessor 自动注册到 delegates Map
}
```

***

## 四、验证步骤

### 步骤 1：运行现有测试，确认基准

**目标：** 确认当前测试状态，作为修改后的对比基准

**操作：**

```bash
# 普通单仓库 - JPA
mvn test -pl structure-infra-sample/structure-infra-sample-jpa -Dtest=UserJpaRepositoryTest

# 普通单仓库 - MyBatis
mvn test -pl structure-infra-sample/structure-infra-sample-mybatis -Dtest=UserRepositoryTest

# CQRS
mvn test -pl structure-infra-sample/structure-infra-sample-cqrs -Dtest=UserCqrsRepositoryTest

# 多仓库
mvn test -pl structure-infra-sample/structure-infra-sample-multi -Dtest=MultiRepositoryTest
```

**记录：** 各测试的通过/失败状态

***

### 步骤 2：修改 RepositoryFacade 支持构造函数注入

**文件：** [RepositoryFacade.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryFacade.java)

**修改内容：**

1. `delegate` 字段改为 `protected final D delegate`
2. 增加带 `D delegate` 参数的构造函数
3. 保留无参构造函数（`protected`，过渡期兼容）

**验证：** 编译通过

***

### 步骤 3：修改 CqrsRepositoryFacade

**文件：** [CqrsRepositoryFacade.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/repository/CqrsRepositoryFacade.java)

**修改内容：**

1. 增加带 `D delegate` 参数的构造函数，调用 `super(delegate)`
2. 保留无参构造函数（过渡期兼容）
3. `readDelegate` 字段保持非 final（由 BeanPostProcessor 注入）

**验证：** 编译通过

***

### 步骤 4：修改 MultiRepositoryFacade

**文件：** [MultiRepositoryFacade.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/repository/MultiRepositoryFacade.java)

**修改内容：**

1. **移除** **`defaultDelegate`** **字段**（冗余，复用父类 `delegate` 字段）
2. 增加带 `D defaultDelegate` 参数的构造函数，调用 `super(defaultDelegate)`
3. 保留无参构造函数（过渡期兼容）
4. `getCurrentDelegate()` 增加回退到 `getDelegate()`（父类）的逻辑
5. 增加 `hasDelegate()` 和 `getRegisteredTypes()` 辅助方法

**验证：** 编译通过

***

### 步骤 5：修改 RepositoryBeanPostProcessor - 职责收缩

**文件：** [RepositoryBeanPostProcessor.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryBeanPostProcessor.java)

**修改内容：**

1. 移除 `implements SmartInitializingSingleton`
2. 移除 `afterSingletonsInstantiated()` 方法
3. 移除 `processRepositoryFacade()` 方法
4. `processCqrsFacade()` 改名为 `processCqrsReadDelegate()`，只注入 readDelegate
5. 移除注入 `delegate` 字段的逻辑（由 Spring 构造函数负责）

**验证：** 编译通过

***

### 步骤 6：修改 MultiRepositoryBeanPostProcessor - 简化 default 处理

**文件：** [MultiRepositoryBeanPostProcessor.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/repository/MultiRepositoryBeanPostProcessor.java)

**修改内容：**

1. `injectDelegatesToMultiFacades()` 中移除设置 defaultDelegate 的逻辑
2. defaultDelegate 由 Spring 构造函数注入

**验证：** 编译通过

***

### 步骤 7：修改示例子类 - 构造函数注入

**修改各示例模块的 RepositoryImpl 类：**

1. **JPA 单仓库：** [UserJpaRepositoryImpl.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-jpa/src/main/java/cn/structure/infra/sample/infra/repository/UserJpaRepositoryImpl.java)

   * 增加带 delegate 参数的构造函数

2. **MyBatis 单仓库：** [UserRepositoryImpl.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-mybatis/src/main/java/cn/structure/infra/sample/infra/repository/UserRepositoryImpl.java)

   * 增加带 delegate 参数的构造函数

3. **CQRS：** [UserCqrsRepository.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-cqrs/src/main/java/cn/structure/infra/sample/cqrs/infra/repository/UserCqrsRepository.java)

   * 增加带 writeDelegate 参数的构造函数

4. **多仓库：** [UserRepositoryImpl.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-multi/src/main/java/cn/structure/infra/sample/multi/infra/repository/UserRepositoryImpl.java)

   * 增加带 defaultDelegate 参数的构造函数

5. **抽象基类：**

   * [AbstractUserRepositoryImpl (core)](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-core/src/main/java/cn/structure/infra/sample/infra/repository/AbstractUserRepositoryImpl.java)

   * [AbstractUserRepositoryImpl (multi)](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-multi/src/main/java/cn/structure/infra/sample/multi/infra/repository/AbstractUserRepositoryImpl.java)

   * 增加带 delegate 参数的构造函数

**验证：** 编译通过

***

### 步骤 8：运行测试验证

**运行所有模块测试：**

```bash
# 普通单仓库 - JPA
mvn test -pl structure-infra-sample/structure-infra-sample-jpa -Dtest=UserJpaRepositoryTest

# 普通单仓库 - MyBatis
mvn test -pl structure-infra-sample/structure-infra-sample-mybatis -Dtest=UserRepositoryTest

# CQRS
mvn test -pl structure-infra-sample/structure-infra-sample-cqrs -Dtest=UserCqrsRepositoryTest

# 多仓库
mvn test -pl structure-infra-sample/structure-infra-sample-multi -Dtest=MultiRepositoryTest
```

**预期结果：**

* 所有测试通过

* 普通单仓库：通过 Spring 构造函数注入 delegate，RepositoryBeanPostProcessor 不干预

* CQRS：base delegate 构造函数注入，readDelegate 由 BeanPostProcessor 注入

* 多仓库：default delegate 构造函数注入，其他 delegates 由 BeanPostProcessor 注册

***

### 步骤 9：边界情况验证

**测试场景：**

1. **普通单仓库 - 构造函数注入正常工作**

   * 验证：delegate 不为 null，操作正常

   * 验证：RepositoryBeanPostProcessor 日志中没有注入普通 RepositoryFacade 的记录

2. **CQRS - base delegate 由 Spring 注入，readDelegate 由 BeanPostProcessor 注入**

   * 验证：delegate（写）不为 null

   * 验证：readDelegate（读）不为 null

   * 验证：读操作优先走 readDelegate

3. **多仓库 - default delegate 由 Spring 注入，其他 delegate 由 BeanPostProcessor 注册**

   * 验证：getDelegate() 返回 default delegate

   * 验证：delegates Map 中有多个 delegate

   * 验证：切换 RepositoryType 后使用不同的 delegate

4. **多仓库模式开关 - multiRepositoryEnabled=true**

   * 验证：RepositoryBeanPostProcessor 跳过 CQRS 处理

   * 验证：MultiRepositoryBeanPostProcessor 正常工作

5. **无参构造函数兼容性（过渡期）**

   * 验证：如果子类不提供带参构造函数，仍然可以通过反射注入（兼容旧代码）

***

## 五、文件清单

### 核心修改文件

| 文件                                                                                                                                                                                                                     | 修改类型 | 修改说明                                                     |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- | -------------------------------------------------------- |
| [RepositoryFacade.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryFacade.java)                                 | 结构修改 | 增加带参构造函数，delegate 改 final                                |
| [CqrsRepositoryFacade.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/repository/CqrsRepositoryFacade.java)                         | 结构修改 | 增加带参构造函数                                                 |
| [MultiRepositoryFacade.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/repository/MultiRepositoryFacade.java)                       | 结构修改 | 移除冗余 defaultDelegate 字段，增加带参构造函数，优化 getCurrentDelegate() |
| [RepositoryBeanPostProcessor.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/repository/RepositoryBeanPostProcessor.java)           | 职责收缩 | 移除普通仓库处理，CQRS 只注入 readDelegate                           |
| [MultiRepositoryBeanPostProcessor.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-starter/src/main/java/cn/structure/infra/repository/MultiRepositoryBeanPostProcessor.java) | 简化   | 移除 defaultDelegate 设置逻辑                                  |

### 示例代码修改文件

| 文件                                                                                                                                                                                                                                                           | 修改类型   | 修改说明                 |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------ | -------------------- |
| [AbstractUserRepositoryImpl (core)](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-core/src/main/java/cn/structure/infra/sample/infra/repository/AbstractUserRepositoryImpl.java)         | 增加构造函数 | 带 delegate 参数        |
| [UserJpaRepositoryImpl.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-jpa/src/main/java/cn/structure/infra/sample/infra/repository/UserJpaRepositoryImpl.java)                      | 增加构造函数 | 带 delegate 参数        |
| [UserRepositoryImpl (mybatis)](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-mybatis/src/main/java/cn/structure/infra/sample/infra/repository/UserRepositoryImpl.java)                   | 增加构造函数 | 带 delegate 参数        |
| [UserCqrsRepository.java](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-cqrs/src/main/java/cn/structure/infra/sample/cqrs/infra/repository/UserCqrsRepository.java)                      | 增加构造函数 | 带 writeDelegate 参数   |
| [AbstractUserRepositoryImpl (multi)](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-multi/src/main/java/cn/structure/infra/sample/multi/infra/repository/AbstractUserRepositoryImpl.java) | 增加构造函数 | 带 delegate 参数        |
| [UserRepositoryImpl (multi)](file:///Users/chuck/projects/structure-projects/structure-pro-infra/structure-infra-sample/structure-infra-sample-multi/src/main/java/cn/structure/infra/sample/multi/infra/repository/UserRepositoryImpl.java)                 | 增加构造函数 | 带 defaultDelegate 参数 |

***

## 六、风险与注意事项

### 6.1 潜在风险

1. **向后兼容性**

   * 风险：现有代码可能依赖无参构造函数 + 反射注入

   * 应对：保留无参构造函数（protected），作为过渡期兼容方案

   * 后续版本：逐步废弃无参构造函数方式

2. **泛型类型解析**

   * 风险：Spring 构造函数注入时，泛型类型 `D` 能否正确解析

   * 应对：子类构造函数使用具体类型（如 `UserJpaDelegate`），Spring 能正确匹配

   * 验证：通过测试确认

3. **多仓库场景下的 bean 冲突**

   * 风险：多仓库模式下，同一实体有多个 delegate bean，Spring 按类型注入会报错

   * 应对：多仓库模式下，用户应使用 `@Qualifier` 或 `@Primary` 指定 default delegate

   * 或者：MultiRepositoryFacade 使用 `Map<String, D>` 让 Spring 注入所有同类型 bean

4. **MongoDB / Elasticsearch 等其他模块**

   * 风险：其他模块的 RepositoryImpl 也需要修改为构造函数注入

   * 应对：检查所有 sample 模块，统一修改

### 6.2 过渡期策略

1. **双轨并行**：构造函数注入和反射注入都支持
2. **优先构造函数**：如果子类提供了带参构造函数，Spring 自动使用
3. **反射兜底**：如果 delegate 为 null（无参构造），由 BeanPostProcessor 反射注入（仅 CQRS 和多仓库）
4. **日志提示**：对使用反射注入的场景打 warn 日志，提醒用户迁移到构造函数注入

***

## 七、验证结论模板

验证完成后，应明确以下结论：

1. ✅ / ❌ 普通单仓库场景：完全由 Spring 构造函数注入，RepositoryBeanPostProcessor 不接管
2. ✅ / ❌ CQRS 场景：base delegate 由 Spring 注入，readDelegate 由 RepositoryBeanPostProcessor 增强注入
3. ✅ / ❌ 多仓库场景：default delegate 由 Spring 注入，其他 delegates 由 MultiRepositoryBeanPostProcessor 注册
4. ✅ / ❌ 三种场景边界清晰，互不干扰
5. ✅ / ❌ 构造函数注入等原生注入方式正常工作
6. 风险点：xxx
7. 建议：xxx

