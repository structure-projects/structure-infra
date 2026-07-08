# structure-infra-xxljob-starter

[XXL-Job](https://github.com/xuxueli/xxl-job) 分布式任务调度框架的适配模块，将 XXL-Job 接入 `structure-infra-schedule-starter` 的 `TaskScheduler` SPI，使应用代码无需感知底层调度实现即可在本地调度与分布式调度之间切换。

## 模块定位

本模块是 `structure-infra-schedule-starter` 的可插拔替代实现：

- 当本模块在 classpath 上时，自动覆盖默认的 `LocalThreadTaskScheduler`
- 所有调度操作通过 `XxlJobTemplate` 转发到远程 XXL-Job admin
- 应用代码仍使用统一的 `TaskScheduler` API，无需修改

## 功能特性

- **自动装配**：`@AutoConfigureBefore(AutoScheduleConfiguration.class)`，自动覆盖本地调度器
- **统一 SPI**：实现 `cn.structure.infra.schedule.TaskScheduler`，应用代码无感知
- **三种调度类型**：
  - `CRON` — 直接使用 `cronExpression`
  - `FIXED_RATE` — 自动转换为 `0/{seconds} * * * * ?` cron 表达式
  - `FIXED_DELAY` — 自动转换为 `0/{seconds} * * * * ?` cron 表达式
- **逻辑 ID 映射**：维护 `taskId <-> xxlJobId` 映射，调用方使用自己的逻辑 `taskId`，无需关心 XXL-Job 内部 job id
- **默认配置**：自动设置路由策略 `FIRST`、阻塞策略 `SERIAL_EXECUTION`、超时 300 秒、重试 1 次、Glue 类型 `BEAN`、作者 `system`
- **完整生命周期**：`schedule / update / remove / pause / resume` 全部映射到 XXL-Job admin REST API

## 依赖

```xml
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-xxljob-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

模块自身依赖：

- `cn.structured:structure-common`
- `cn.structured:structure-infra-schedule-starter` — 提供 `TaskScheduler` SPI、`ScheduleTask` 模型、`TaskHandler`、`TaskHandlerRegistry`
- `cn.structured:structure-job-starter`（v2.0.0，外部）— 提供 `XxlJobClient`、`XxlJobInfoDTO`、`ExecutorRouteStrategyEnum`
- `org.springframework.boot:spring-boot-autoconfigure`
- `org.springframework.boot:spring-boot-configuration-processor`（optional）

## 自动配置

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册：

```
cn.structure.infra.configuration.AutoXxlJobConfiguration
```

`AutoXxlJobConfiguration` 通过 `@AutoConfigureBefore(AutoScheduleConfiguration.class)` 在 schedule-starter 的本地调度器之前注册，使 `@ConditionalOnMissingBean(TaskScheduler.class)` 在 schedule-starter 一侧回退：

| Bean | 类型 | 条件 |
|------|------|------|
| `xxlJobTemplate` | `XxlJobTemplateImpl` | `@ConditionalOnMissingBean(XxlJobTemplate.class)`，依赖外部 `XxlJobClient` bean |
| `taskScheduler` | `XxlJobTaskScheduler` | `@ConditionalOnMissingBean(TaskScheduler.class)` |

> **前提**：上下文中必须已存在 `XxlJobClient` bean（由外部 `structure-job-starter` 在 `structure.job.*` 配置下自动注册）。

## 配置属性

### 本模块专属（`structure.schedule.xxl-job.*`）

`XxlJobProperties`：

| 属性 | 类型 | 默认值 | 说明 |
|-----|------|-------|------|
| `enabled` | `boolean` | `true` | 开关（声明但当前未通过 `@ConditionalOnProperty` 强制） |
| `jobGroup` | `Integer` | `1` | XXL-Job 执行器分组 ID，写入每个 `XxlJobInfoDTO` |

```yaml
structure:
  schedule:
    xxl-job:
      enabled: true
      job-group: 1
```

### 外部 `structure-job-starter`（`structure.job.*`）

XXL-Job 的 admin 地址、appname、access token、executor 端口、日志路径等由外部 `structure-job-starter` 管理：

```yaml
structure:
  job:
    enable: true
    admin-address: http://localhost:8080/xxl-job-admin
    access-token: xxl-job-admin-token
    executor:
      appname: my-app-executor
```

## 核心类

### `XxlJobTemplate`（接口）

对 `XxlJobClient` 的薄封装，所有操作返回/接收 XXL-Job 内部 job id：

```java
String add(String jobName, String cronExpression, String handlerName, String handlerParam);
void   update(String jobId, String jobName, String cronExpression, String handlerName, String handlerParam);
void   remove(String jobId);
void   pause(String jobId);
void   start(String jobId);
String getJobId(String handlerName);  // 当前为 stub，返回 null
```

### `XxlJobTemplateImpl`

`XxlJobTemplate` 的默认实现：

- 每次 CRUD 操作构建 `XxlJobInfoDTO`（设置 jobGroup、路由 `FIRST`、阻塞 `SERIAL_EXECUTION`、超时 300s、重试 1、glueType `BEAN`、author `system`）
- 调用对应 `XxlJobClient` 方法
- 检查返回 `Response<String>` 的 code，成功则返回 job id，失败抛 `RuntimeException`

### `XxlJobTaskScheduler`

`TaskScheduler` SPI 的 XXL-Job 实现：

内部状态：

```java
private final Map<String, String>       taskIdToXxlJobIdMap = new ConcurrentHashMap<>();
private final Map<String, ScheduleTask> taskMap             = new ConcurrentHashMap<>();
```

| 方法 | 行为 |
|------|------|
| `schedule(task)` | 校验 → `remove(taskId)` 清理旧映射 → 转换为 cron → `xxlJobTemplate.add(...)` → 存储 `taskId -> xxlJobId` 与 task → status=RUNNING |
| `update(task)` | 无 xxlJobId 则回退到 `schedule(task)`；否则调用 `xxlJobTemplate.update(...)` → status=RUNNING |
| `remove(taskId)` | 移除两个映射；如有 xxlJobId 则调用 `xxlJobTemplate.remove(xxlJobId)`；status=STOPPED |
| `pause(taskId)` | 查找 xxlJobId → `xxlJobTemplate.pause(xxlJobId)` → status=PAUSED |
| `resume(taskId)` | 仅当 status=PAUSED 时执行 → `xxlJobTemplate.start(xxlJobId)` → status=RUNNING |
| `getTaskInfo(taskId)` | 返回缓存的 `ScheduleTask`（或 null） |
| `getAllTasks()` | 返回所有缓存任务的不可变副本 |

Cron 转换逻辑：

```java
private String convertToCron(ScheduleTask task) {
    if (task.getScheduleType() == ScheduleType.CRON) {
        if (cronExpression == null || empty) throw IllegalArgumentException;
        return cronExpression;
    }
    long ms = (scheduleType == FIXED_RATE) ? period : delay;
    long seconds = Math.max(1, ms / 1000);
    return "0/" + seconds + " * * * * ?";
}
```

## 使用示例

### 1. 配置

```yaml
structure:
  job:
    enable: true
    admin-address: http://localhost:8080/xxl-job-admin
    access-token: xxl-job-admin-token
    executor:
      appname: my-app-executor
  schedule:
    xxl-job:
      enabled: true
      job-group: 1
```

### 2. 声明 XXL-Job 处理器

使用 XXL-Job 的 `@XxlJob` 注解，注解值必须与后续 `ScheduleTask.handlerName` 一致：

```java
@Slf4j
@Component
public class SampleXxlJob {

    private final AtomicInteger counter = new AtomicInteger(0);

    @XxlJob("sampleJobHandler")
    public void sampleJobHandler() {
        int count = counter.incrementAndGet();
        log.info("SampleXxlJob executed, count={}", count);
    }

    @XxlJob("simpleJobHandler")
    public void simpleJobHandler() {
        log.info("SimpleXxlJob executed");
    }
}
```

### 3. 通过 `TaskScheduler` 调度任务

```java
@Autowired
private TaskScheduler taskScheduler;

// CRON 调度
ScheduleTask task = ScheduleTask.builder()
        .taskId("order-sync-001")
        .taskName("每日订单同步")
        .handlerName("sampleJobHandler")    // 必须匹配 @XxlJob 的值
        .handlerParam("...")
        .scheduleType(ScheduleTask.ScheduleType.CRON)
        .cronExpression("0/10 * * * * ?")
        .build();
taskScheduler.schedule(task);

// FIXED_RATE 调度（自动转换为 cron）
ScheduleTask fixedRateTask = ScheduleTask.builder()
        .taskId("metrics-collect-001")
        .taskName("指标采集")
        .handlerName("simpleJobHandler")
        .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
        .period(5000L)   // 自动转换为 0/5 * * * * ?
        .build();
taskScheduler.schedule(fixedRateTask);

// 生命周期管理
taskScheduler.pause("order-sync-001");
taskScheduler.resume("order-sync-001");
taskScheduler.update(task);
taskScheduler.remove("order-sync-001");
taskScheduler.getTaskInfo("order-sync-001");
taskScheduler.getAllTasks();
```

### 4. 通过 REST API 管理（参考示例模块）

```java
@RestController
@RequestMapping("/job")
@RequiredArgsConstructor
public class JobManagerController {

    private final TaskScheduler taskScheduler;

    @PostMapping("/add")
    public ScheduleTask add(@RequestParam String taskId,
                            @RequestParam String taskName,
                            @RequestParam String handlerName,
                            @RequestParam(required = false) String handlerParam,
                            @RequestParam(defaultValue = "0/5 * * * * ?") String cronExpression) {
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

    @PutMapping("/update/{taskId}")
    public ScheduleTask update(@PathVariable String taskId, @RequestParam String taskName,
                               @RequestParam(required = false) String handlerParam,
                               @RequestParam(defaultValue = "0/5 * * * * ?") String cronExpression) {
        ScheduleTask exist = taskScheduler.getTaskInfo(taskId);
        ScheduleTask task = ScheduleTask.builder()
                .taskId(taskId)
                .taskName(taskName != null ? taskName : exist.getTaskName())
                .handlerName(exist.getHandlerName())
                .handlerParam(handlerParam != null ? handlerParam : exist.getHandlerParam())
                .scheduleType(ScheduleTask.ScheduleType.CRON)
                .cronExpression(cronExpression)
                .build();
        taskScheduler.update(task);
        return taskScheduler.getTaskInfo(taskId);
    }

    @DeleteMapping("/remove/{taskId}")
    public void remove(@PathVariable String taskId) { taskScheduler.remove(taskId); }

    @PutMapping("/pause/{taskId}")
    public void pause(@PathVariable String taskId) { taskScheduler.pause(taskId); }

    @PutMapping("/resume/{taskId}")
    public void resume(@PathVariable String taskId) { taskScheduler.resume(taskId); }

    @GetMapping("/info/{taskId}")
    public ScheduleTask info(@PathVariable String taskId) { return taskScheduler.getTaskInfo(taskId); }

    @GetMapping("/list")
    public List<ScheduleTask> list() { return taskScheduler.getAllTasks(); }
}
```

## 默认 job 配置

`XxlJobTemplateImpl.buildJobInfo(...)` 自动应用以下默认值：

| 字段 | 默认值 |
|------|-------|
| `executorRouteStrategy` | `FIRST` |
| `executorBlockStrategy` | `SERIAL_EXECUTION` |
| `executorTimeout` | `300`（秒） |
| `executorFailRetryCount` | `1` |
| `glueType` | `BEAN` |
| `author` | `system` |
| `jobGroup` | `XxlJobProperties.jobGroup`（默认 1） |

## 注意事项

- **`XxlJobTemplate.getJobId(handlerName)`** 当前为 stub，始终返回 `null`
- **`XxlJobProperties.enabled`** 当前未通过 `@ConditionalOnProperty` 强制生效；设为 `false` 不会阻止自动配置
- **状态不持久化**：`taskId -> xxlJobId` 映射存储在内存中，应用重启后丢失（XXL-Job admin 端的 job 仍然存在）
- **不支持的 ScheduleTask 字段**：`initialDelay` 与 `timeUnit` 在 XXL-Job 实现中被忽略；仅使用 `cronExpression`、`period`（FIXED_RATE）、`delay`（FIXED_DELAY，按毫秒处理）
- **FIXED_RATE / FIXED_DELAY 转换**：自动生成 `0/{seconds} * * * * ?`，亚秒级周期会被向上取整为 1 秒
- **依赖外部 `XxlJobClient`**：必须配置 `structure.job.*` 让 `structure-job-starter` 注册 `XxlJobClient` bean，否则 `AutoXxlJobConfiguration` 启动失败

## 与本地调度的切换

| 场景 | 引入的 starter | 生效的 `TaskScheduler` |
|------|---------------|----------------------|
| 单机本地调度 | `structure-infra-schedule-starter` | `LocalThreadTaskScheduler` |
| 分布式 XXL-Job 调度 | `structure-infra-schedule-starter` + `structure-infra-xxljob-starter` | `XxlJobTaskScheduler`（通过 `@AutoConfigureBefore` 覆盖） |

业务代码完全一致，仅依赖切换即可。

## 测试

参考示例模块 `structure-infra-sample-xxljob`：

```bash
mvn test -pl structure-infra-sample/structure-infra-sample-xxljob
```

测试需启动 XXL-Job admin 服务（默认 `http://localhost:8080/xxl-job-admin`）。

## License

Apache License 2.0
