# structure-infra-schedule-starter

轻量级本地任务调度框架，基于 Java `ScheduledExecutorService` 提供注册表驱动的动态调度 API。无需外部依赖，适合单机应用或需要程序化调度的场景。

## 功能特性

- **动态调度**：运行时通过 `TaskScheduler` API 完成 `schedule / update / remove / pause / resume` 等操作，无需重启应用
- **三种调度类型**：`CRON`、`FIXED_DELAY`、`FIXED_RATE`
- **处理器注册表**：通过 `TaskHandlerRegistry` 按名称查找处理器，解耦任务定义与执行逻辑
- **程序化构建器**：`ScheduleTask.builder()` API，无需注解
- **Spring TaskScheduler 适配**：通过 `SpringTaskSchedulerAdapter` 暴露为 Spring 的 `org.springframework.scheduling.TaskScheduler`，让 `@Scheduled` 等基础设施复用同一调度引擎
- **守护线程池**：通过 `structure.schedule.pool-size` 配置线程池大小（默认 CPU 核心数）
- **幂等调度**：`schedule(taskId)` 会先取消同 ID 的旧任务
- **错误隔离**：处理器抛出的异常会被捕获并记录日志，不会终止周期性调度
- **自动配置**：基于 Spring Boot 3+ AutoConfiguration SPI

## 依赖

```xml
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-schedule-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

模块自身依赖：

- `cn.structured:structure-common`
- `org.springframework.boot:spring-boot-starter`
- `org.springframework.boot:spring-boot-autoconfigure`
- `org.springframework.boot:spring-boot-configuration-processor`（optional）

## 自动配置

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册：

```
cn.structure.infra.configuration.AutoScheduleConfiguration
```

注册的 Bean：

| Bean | 类型 | 条件 |
|------|------|------|
| `taskHandlerRegistry` | `DefaultTaskHandlerRegistry` | `@ConditionalOnMissingBean(TaskHandlerRegistry.class)` |
| `taskScheduler` | `LocalThreadTaskScheduler` | `@ConditionalOnMissingBean(TaskScheduler.class)`，线程池大小来自 `ScheduleProperties` |
| `springTaskScheduler` | `SpringTaskSchedulerAdapter`（实现 Spring `TaskScheduler`） | `@ConditionalOnBean(LocalThreadTaskScheduler.class)` |

## 配置属性

`ScheduleProperties`（前缀 `structure.schedule`）：

| 属性 | 类型 | 默认值 | 说明 |
|-----|------|-------|------|
| `poolSize` | `Integer` | `Runtime.getRuntime().availableProcessors()` | 调度线程池大小 |

```yaml
structure:
  schedule:
    pool-size: 4
```

## 核心类

### `ScheduleTask`（任务定义 POJO）

Lombok `@Data @Builder`，**不是注解**。字段：

| 字段 | 类型 | 说明 |
|-----|------|------|
| `taskId` | `String` | 任务唯一标识 |
| `taskName` | `String` | 人类可读名称 |
| `handlerName` | `String` | 必须与注册表中的名称匹配 |
| `handlerParam` | `String` | 传递给处理器的参数 |
| `scheduleType` | `ScheduleType` | `CRON` / `FIXED_DELAY` / `FIXED_RATE` |
| `cronExpression` | `String` | `CRON` 类型必填 |
| `initialDelay` | `Long` | 首次执行延迟（默认 0） |
| `delay` | `Long` | `FIXED_DELAY` 使用 |
| `period` | `Long` | `FIXED_RATE` 使用 |
| `timeUnit` | `TimeUnit` | 默认 `MILLISECONDS` |
| `status` | `TaskStatus` | `PENDING` / `RUNNING` / `PAUSED` / `STOPPED` |

### `TaskHandler`（函数式接口）

```java
@FunctionalInterface
public interface TaskHandler {
    void execute(String param);
}
```

### `TaskHandlerRegistry`

```java
void register(String handlerName, TaskHandler handler);
TaskHandler get(String handlerName);
void unregister(String handlerName);
boolean contains(String handlerName);
```

默认实现 `DefaultTaskHandlerRegistry` 基于 `ConcurrentHashMap`。

### `TaskScheduler`

```java
void schedule(ScheduleTask task);
void update(ScheduleTask task);
void remove(String taskId);
void pause(String taskId);
void resume(String taskId);
ScheduleTask getTaskInfo(String taskId);
List<ScheduleTask> getAllTasks();
```

### `LocalThreadTaskScheduler`

默认 `TaskScheduler` 实现：

- 通过 `Executors.newScheduledThreadPool(poolSize, threadFactory)` 创建调度器，线程为守护线程，命名为 `structure-schedule-<id>`
- `schedule(task)` 校验 → 先 `remove(taskId)` 取消旧任务 → 包装 Runnable 通过注册表查找 handler → 按 `scheduleType` 派发
- `FIXED_DELAY` / `FIXED_RATE` 默认间隔 1000ms
- `CRON` 基于 Spring `CronExpression` 解析 6 字段 cron 表达式，按下次触发时间递归一次性调度，严格遵循 cron 语义
- `update(task)` 等同于 `schedule(task)`（先取消再调度）
- `pause(taskId)` 取消 future 但保留任务信息
- `resume(taskId)` 仅当 `status == PAUSED` 时重新调度
- 处理器异常被捕获并记录日志，不影响后续周期

### `SpringTaskSchedulerAdapter`

实现 Spring 的 `org.springframework.scheduling.TaskScheduler`，将 Spring 调度基础设施（如 `@Scheduled`）路由到 `LocalThreadTaskScheduler`。支持 `schedule(Runnable, Trigger)` / `schedule(Runnable, Instant)` / `scheduleAtFixedRate` / `scheduleWithFixedDelay` 等全部方法。

## 使用示例

### 1. 注册处理器

```java
@Slf4j
@Component
public class DemoTaskHandlers {

    @Autowired
    private TaskHandlerRegistry handlerRegistry;

    @PostConstruct
    public void registerHandlers() {
        handlerRegistry.register("demo-fixed-rate-handler", this::fixedRateHandler);
        handlerRegistry.register("demo-param-handler", this::paramHandler);
        handlerRegistry.register("demo-error-handler", this::errorHandler);
    }

    public void fixedRateHandler(String param) {
        log.info("固定速率任务执行，参数：{}", param);
    }

    public void paramHandler(String param) {
        log.info("带参数任务执行，参数：{}", param);
    }

    public void errorHandler(String param) {
        log.info("抛出异常的任务");
        throw new RuntimeException("模拟任务执行异常");
    }
}
```

### 2. 调度任务

```java
@Slf4j
@Configuration
@DependsOn("demoTaskHandlers")
public class ScheduleDemoConfig {

    @Autowired
    private TaskScheduler taskScheduler;

    @PostConstruct
    public void initScheduledTasks() {
        // 固定速率：每 3 秒执行一次，1 秒后启动
        ScheduleTask fixedRateTask = ScheduleTask.builder()
                .taskId("demo-fixed-rate-task")
                .taskName("固定速率示例任务")
                .handlerName("demo-fixed-rate-handler")
                .handlerParam("fixed-rate-param")
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(3000L)
                .initialDelay(1000L)
                .timeUnit(TimeUnit.MILLISECONDS)
                .build();
        taskScheduler.schedule(fixedRateTask);

        // 固定延迟：上一次结束 2 秒后执行下一次
        ScheduleTask fixedDelayTask = ScheduleTask.builder()
                .taskId("demo-fixed-delay-task")
                .taskName("固定延迟示例任务")
                .handlerName("demo-fixed-delay-handler")
                .handlerParam("fixed-delay-param")
                .scheduleType(ScheduleTask.ScheduleType.FIXED_DELAY)
                .delay(2000L)
                .initialDelay(2000L)
                .timeUnit(TimeUnit.MILLISECONDS)
                .build();
        taskScheduler.schedule(fixedDelayTask);
    }
}
```

### 3. 运行时通过 REST API 管理任务

```java
@RestController
@RequestMapping("/job")
@RequiredArgsConstructor
public class JobManagerController {

    private final TaskScheduler taskScheduler;

    @PostMapping("/add")
    public ScheduleTask add(@RequestParam("taskId") String taskId,
                            @RequestParam("taskName") String taskName,
                            @RequestParam("handlerName") String handlerName,
                            @RequestParam(value = "handlerParam", required = false) String handlerParam,
                            @RequestParam(value = "cronExpression",
                                          defaultValue = "0/5 * * * * ?") String cronExpression) {
        ScheduleTask task = ScheduleTask.builder()
                .taskId(taskId)
                .taskName(taskName)
                .handlerName(handlerName)
                .handlerParam(handlerParam)
                .scheduleType(ScheduleTask.ScheduleType.CRON)
                .cronExpression(cronExpression)
                .build();
        taskScheduler.schedule(task);
        return taskScheduler.getTaskInfo(taskId);
    }

    @PutMapping("/pause/{taskId}")
    public void pause(@PathVariable String taskId) {
        taskScheduler.pause(taskId);
    }

    @PutMapping("/resume/{taskId}")
    public void resume(@PathVariable String taskId) {
        taskScheduler.resume(taskId);
    }

    @DeleteMapping("/remove/{taskId}")
    public void remove(@PathVariable String taskId) {
        taskScheduler.remove(taskId);
    }

    @GetMapping("/list")
    public List<ScheduleTask> list() {
        return taskScheduler.getAllTasks();
    }
}
```

## 校验规则

`schedule(task)` 会校验以下条件，违反时抛出 `IllegalArgumentException`：

- `task` 不能为 null
- `taskId` 不能为 null
- `handlerName` 不能为 null 或空字符串
- `handlerName` 必须在 `TaskHandlerRegistry` 中已注册
- `scheduleType` 不能为 null
- `CRON` 类型的 `cronExpression` 不能为 null 或空

## 注意事项

- **CRON 表达式**：基于 Spring `CronExpression` 解析标准 6 字段 cron（秒 分 时 日 月 周），支持 `?` / `L` / `W` / `#` 等 Quartz 风格语法。采用递归一次性调度模式按下次触发时间精确触发。如需分布式调度，请使用 `structure-infra-xxljob-starter`
- **状态持久化**：任务状态存储在内存中（`ConcurrentHashMap`），JVM 重启后丢失
- **集群支持**：本模块为单机调度器，不支持分布式协调。如需分布式调度，请使用 `structure-infra-xxljob-starter`
- **守护线程**：调度线程为守护线程，不会阻止 JVM 退出

## 测试

模块自带单元测试：

```bash
mvn test -pl structure-infra-schedule-starter
```

测试覆盖：

- `DefaultTaskHandlerRegistryTest` — register / get / contains / unregister / 空值拒绝 / 覆盖行为
- `LocalThreadTaskSchedulerTest` — schedule / pause / resume / remove / update / getAllTasks 及全部校验路径
- `SpringTaskSchedulerAdapterTest` — 所有 Spring `TaskScheduler` 方法

集成测试参考示例模块 `structure-infra-sample-schedule`：

```bash
mvn test -pl structure-infra-sample/structure-infra-sample-schedule
```

## License

Apache License 2.0
