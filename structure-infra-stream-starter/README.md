# Structure Infra Stream Starter

基于 Spring Cloud Stream 的事件监听管理器，提供灵活的事件监听配置和统一路由能力。

## 功能特性

- **动态配置**：支持动态配置 Stream Binding，替代静态配置
- **自动绑定**：根据注解自动生成 binding 配置，无需手动配置
- **统一路由**：基于 eventType/businessType/condition 的统一事件路由
- **声明式注册**：通过注解自动注册事件处理器
- **运行时动态注册**：支持运行时动态注册监听器和绑定
- **低代码支持**：支持配置文件驱动、代码动态注册等多种使用方式
- **SpEL 条件路由**：支持 SpEL 表达式进行条件过滤
- **业务解耦**：事件生产者和消费者完全解耦

## 快速开始

### 添加依赖

```xml
<dependency>
    <groupId>cn.structured</groupId>
    <artifactId>structure-infra-stream-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 基本配置

```yaml
structure:
  infra:
    stream:
      enabled: true
      auto-binding: true           # 自动生成 binding 配置（默认开启）
      default-group: my-service    # 所有自动绑定的默认消费组
      default-concurrency: 1       # 默认并发数
```

> **重要**：启用 `auto-binding` 后，无需手动配置 `spring.cloud.stream.bindings`，系统会根据注解自动生成！

## 核心概念

### StreamEvent 统一事件信封

所有事件通过 `StreamEvent<T>` 封装，包含路由元数据：

```java
public class StreamEvent<T> {
    private String eventId;           // 事件唯一标识
    private String eventType;         // 事件类型（用于路由）
    private String businessType;      // 业务类型（可选）
    private LocalDateTime timestamp;  // 时间戳
    private T payload;                // 业务数据
    private Map<String, String> headers; // 扩展头信息
    private String traceId;           // 链路追踪ID
}
```

## 使用方式

### 方式一：注解声明式（推荐）

通过 `@StreamRouteHandler` 注解声明路由，自动注册：

```java
@Component
public class OrderEventListener {
    
    @StreamRouteHandler(eventType = "orderCreated")
    public void handleOrderCreated(OrderEvent event) {
        log.info("[订单创建] orderId={}, amount={}", event.getOrderId(), event.getAmount());
    }
    
    @StreamRouteHandler(eventType = "orderCreated", condition = "#payload.amount > 1000")
    public void handleHighAmountOrder(OrderEvent event) {
        log.info("[高额订单] 触发风控检查: orderId={}, amount={}", event.getOrderId(), event.getAmount());
    }
    
    @StreamRouteHandler(eventType = "orderPaid")
    public void handleOrderPaid(OrderEvent event) {
        log.info("[订单支付] orderId={}", event.getOrderId());
    }
    
    @StreamRouteHandler(eventType = "orderCancelled", businessType = "retail")
    public void handleRetailOrderCancelled(OrderEvent event) {
        log.info("[零售订单取消] orderId={}", event.getOrderId());
    }
}
```

#### @StreamRouteHandler 注解参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| value / eventType | String | 是 | 事件类型，用于路由匹配 |
| businessType | String | 否 | 业务类型，支持 `*` 通配符 |
| condition | String | 否 | SpEL 表达式条件过滤 |

### 方式二：代码动态注册

通过 `StreamEventRouter` API 动态注册路由：

```java
@Component
public class RouteConfig implements CommandLineRunner {
    
    private final StreamEventRouter eventRouter;
    
    @Override
    public void run(String... args) {
        // 注册基础路由
        eventRouter.registerRoute("orderCreated", OrderEvent.class, (payload, event) -> {
            log.info("[订单创建] orderId={}", payload.getOrderId());
        });
        
        // 注册带条件的路由
        eventRouter.registerRoute("orderCreated", OrderEvent.class, 
            "#payload.amount > 1000", (payload, event) -> {
                log.info("[高额订单] 触发风控检查");
            });
        
        // 注册带业务类型的路由
        eventRouter.registerRoute("orderCreated", "wholesale", OrderEvent.class, (payload, event) -> {
            log.info("[批发订单] orderId={}", payload.getOrderId());
        });
    }
}
```

### 方式三：配置文件驱动

通过 YAML 配置文件声明路由，真正的低代码：

```yaml
structure:
  infra:
    stream:
      router:
        enabled: true
        routes:
          - id: route-order-created
            event-type: orderCreated
            payload-type: cn.structure.infra.sample.stream.event.OrderEvent
            handler-bean: orderHandler
            handler-method: onOrderCreated
            description: 处理订单创建事件
            
          - id: route-payment-success
            event-type: paymentSuccess
            payload-type: cn.structure.infra.sample.stream.event.PaymentEvent
            handler-bean: paymentHandler
            handler-method: onPaymentSuccess
            description: 处理支付成功事件
            
          - id: route-high-amount-order
            event-type: orderCreated
            payload-type: cn.structure.infra.sample.stream.event.OrderEvent
            condition: "#payload.amount > 1000"
            handler-bean: orderHandler
            handler-method: onHighAmountOrder
            description: 处理高额订单（金额>1000）
```

业务处理器（只需写方法，无需注解）：

```java
@Component("orderHandler")
public class OrderHandler {
    
    public void onOrderCreated(OrderEvent event) {
        log.info("[配置驱动] 订单创建处理: orderId={}", event.getOrderId());
    }
    
    public void onHighAmountOrder(OrderEvent event) {
        log.info("[配置驱动] 高额订单处理: orderId={}, amount={}", event.getOrderId(), event.getAmount());
    }
}
```

### 方式四：传统消息监听

通过 `@StreamEventListener` 绑定到特定消息队列：

```java
@Component
public class DeliveryEventListener {
    
    @StreamEventListener(bindingName = "deliveryEvent", 
                         destination = "delivery-exchange", 
                         group = "delivery-group")
    public void handleDelivery(DeliveryEvent event) {
        log.info("[配送] deliveryId={}, orderId={}", event.getDeliveryId(), event.getOrderId());
    }
}
```

#### @StreamEventListener 注解参数

### 方式五：运行时动态注册（推荐）

支持在应用运行时动态注册监听器和绑定，无需重启应用。

#### 通过 StreamEventManager 动态注册

```java
@Component
public class DynamicListenerDemo implements CommandLineRunner {
    
    private final StreamEventManager eventManager;
    
    @Override
    public void run(String... args) {
        // 1. 动态注册 binding
        eventManager.registerBinding("dynamicOrder", "dynamic-order-exchange", "dynamic-group");
        
        // 2. 通过实现类注册监听器
        eventManager.registerListener("dynamicOrder", OrderEvent.class, new OrderEventHandler());
        
        // 3. 通过 Lambda 注册监听器
        eventManager.registerListener("dynamicOrder", OrderEvent.class, event -> {
            log.info("[Lambda] 动态监听订单: orderId={}", event.getOrderId());
        });
        
        // 4. 通过 Lambda + SpEL 条件注册监听器
        eventManager.registerListener("dynamicOrder", OrderEvent.class, 
            "#payload.amount > 500", event -> {
                log.info("[条件] 动态监听大额订单: orderId={}", event.getOrderId());
            });
        
        // 5. 动态发布事件（自动创建 binding）
        eventManager.publish("dynamicPayment", "dynamic-payment-exchange", paymentEvent);
    }
    
    public static class OrderEventHandler implements StreamEventHandler<OrderEvent> {
        @Override
        public void handle(OrderEvent event) {
            log.info("[实现类] 动态监听订单: orderId={}", event.getOrderId());
        }
    }
}
```

#### 通过 StreamEventRouter 动态注册路由

```java
@Component
public class DynamicRouteDemo implements CommandLineRunner {
    
    private final StreamEventRouter eventRouter;
    
    @Override
    public void run(String... args) {
        // 1. 通过实现类注册路由
        eventRouter.registerRoute("orderCreated", OrderEvent.class, new OrderRouteHandler());
        
        // 2. 通过 Lambda 注册路由
        eventRouter.registerRoute("paymentSuccess", PaymentEvent.class, (payload, event) -> {
            log.info("[Lambda] 支付成功: paymentId={}", payload.getPaymentId());
        });
        
        // 3. 注册通用路由（Object 类型）
        eventRouter.registerRoute("genericEvent", Object.class, (payload, event) -> {
            log.info("[通用] 事件: payload={}", payload);
        });
        
        // 4. 注册带业务类型的路由
        eventRouter.registerRoute("orderCreated", "retail", OrderEvent.class, (payload, event) -> {
            log.info("[零售] 订单: orderId={}", payload.getOrderId());
        });
        
        // 5. 分发事件
        eventRouter.route(StreamEvent.of("orderCreated", orderEvent));
    }
    
    public static class OrderRouteHandler implements StreamEventRouter.StreamRouteHandler<OrderEvent> {
        @Override
        public void handle(OrderEvent payload, StreamEvent<OrderEvent> event) {
            log.info("[实现类] 订单路由: orderId={}", payload.getOrderId());
        }
    }
}
```

#### 动态注册 API 汇总

| API | 说明 |
|-----|------|
| `eventManager.registerBinding(name, destination)` | 动态注册 binding |
| `eventManager.registerBinding(name, destination, group)` | 动态注册 binding（指定消费组） |
| `eventManager.registerListener(name, type, handler)` | 动态注册监听器 |
| `eventManager.registerListener(name, type, condition, handler)` | 动态注册带条件的监听器 |
| `eventManager.publish(name, destination, event)` | 动态发布事件（自动创建 binding） |
| `eventManager.unregisterBinding(name)` | 注销 binding |
| `eventManager.unregisterListener(name)` | 注销监听器 |
| `eventRouter.registerRoute(eventType, payloadType, handler)` | 动态注册路由 |
| `eventRouter.registerRoute(eventType, businessType, payloadType, handler)` | 动态注册带业务类型的路由 |
| `eventRouter.unregisterRoute(eventType)` | 注销路由 |

#### @StreamEventListener 注解参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| value / bindingName | String | 是 | Binding 名称 |
| destination | String | 否 | 消息队列目的地 |
| group | String | 否 | 消费组 |
| contentType | String | 否 | 内容类型，默认 application/json |
| eventType | Class | 否 | 事件类型 |
| condition | String | 否 | SpEL 条件表达式 |

## 事件发布

### 方式一：使用 StreamEventManager 发布

```java
@Component
public class OrderService {
    
    private final StreamEventManager eventManager;
    
    public void createOrder(Order order) {
        OrderEvent event = OrderEvent.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .amount(order.getAmount())
                .build();
        
        // 发布到已配置的 binding
        eventManager.publish("orderEvent", event);
    }
}
```

### 方式二：使用 StreamEvent 封装发布

```java
@Component
public class EventPublisher {
    
    private final StreamEventRouter eventRouter;
    
    public void publishOrderCreated(OrderEvent event) {
        // 封装为统一事件信封
        StreamEvent<OrderEvent> streamEvent = StreamEvent.of("orderCreated", event);
        // 路由分发
        eventRouter.route(streamEvent);
    }
    
    public void publishPaymentSuccess(PaymentEvent event) {
        StreamEvent<PaymentEvent> streamEvent = StreamEvent.of("paymentSuccess", "retail", event);
        eventRouter.route(streamEvent);
    }
}
```

## 配置说明

### Stream 绑定配置

```yaml
structure:
  infra:
    stream:
      enabled: true                    # 是否启用
      bindings:
        orderEvent:                    # binding 名称
          destination: order-exchange  # 消息队列目的地
          content-type: application/json
          group: order-group           # 消费组
          binder: kafka                # 绑定器（可选）
          concurrency: 3               # 并发数（可选）
```

### 路由配置

```yaml
structure:
  infra:
    stream:
      router:
        enabled: true                  # 是否启用配置驱动路由
        routes:                        # 路由列表
          - id: route-001              # 路由唯一标识
            event-type: orderCreated   # 事件类型
            business-type: retail      # 业务类型（可选）
            payload-type: com.example.OrderEvent  # 负载类型全限定名
            condition: "#payload.amount > 1000"    # SpEL 条件（可选）
            handler-bean: orderHandler # 处理器 Bean 名称
            handler-method: onOrderCreated        # 处理器方法名
            description: 订单创建事件处理           # 描述（可选）
```

## API 文档

### StreamEventRouter

| 方法 | 说明 |
|------|------|
| `registerRoute(eventType, payloadType, handler)` | 注册路由 |
| `registerRoute(eventType, payloadType, condition, handler)` | 注册带条件的路由 |
| `registerRoute(eventType, businessType, payloadType, handler)` | 注册带业务类型的路由 |
| `registerRoute(eventType, businessType, payloadType, condition, handler)` | 注册完整路由 |
| `unregisterRoute(eventType)` | 注销指定事件类型的所有路由 |
| `unregisterRoute(eventType, handlerId)` | 注销指定路由 |
| `route(event)` | 路由分发事件 |
| `isRouteRegistered(eventType)` | 检查路由是否已注册 |
| `getRoutes(eventType)` | 获取指定事件类型的路由列表 |

### StreamEventManager

| 方法 | 说明 |
|------|------|
| `publish(bindingName, event)` | 发布事件到指定 binding |
| `publish(bindingName, destination, event)` | 发布事件到指定目的地 |
| `registerListener(bindingName, eventType, handler)` | 注册监听器 |
| `registerListener(bindingName, destination, group, eventType, handler)` | 注册完整监听器 |
| `unregisterListener(bindingName)` | 注销监听器 |
| `dispatch(bindingName, event)` | 分发给注册的监听器 |

## 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                        事件发布层                               │
│  StreamEventManager / StreamEventRouter.route()               │
└───────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     StreamEvent 统一信封                        │
│  { eventType, businessType, payload, headers, traceId }        │
└───────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   StreamEventRouter 路由网关                     │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 路由匹配规则:                                              │  │
│  │ 1. eventType 精确匹配                                      │  │
│  │ 2. businessType 匹配（支持 * 通配符）                      │  │
│  │ 3. payloadType 类型匹配                                    │  │
│  │ 4. condition SpEL 表达式匹配                              │  │
│  └───────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
   ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
   │   Handler A     │ │   Handler B     │ │   Handler C     │
   │ orderCreated    │ │ orderCreated    │ │ orderPaid       │
   │ condition: >1000│ │ business: retail│ │                 │
   └─────────────────┘ └─────────────────┘ └─────────────────┘
```

## 目录结构

```
structure-infra-stream-starter/
├── src/main/java/cn/structure/infra/stream/
│   ├── annotation/                # 注解定义
│   │   ├── StreamEventListener.java
│   │   └── StreamRouteHandler.java
│   ├── configuration/             # 自动配置
│   │   └── StreamAutoConfiguration.java
│   ├── event/                     # 事件模型
│   │   └── StreamEvent.java
│   ├── handler/                   # 处理器接口
│   │   └── StreamEventHandler.java
│   ├── manager/                   # 事件管理器
│   │   ├── StreamEventManager.java
│   │   └── DefaultStreamEventManagerImpl.java
│   ├── processor/                 # Bean 后置处理器
│   │   └── EventListenerBeanPostProcessor.java
│   ├── properties/                # 配置属性
│   │   └── StreamProperties.java
│   └── router/                    # 路由模块
│       ├── StreamEventRouter.java
│       ├── DefaultStreamEventRouterImpl.java
│       ├── RouteHandlerBeanPostProcessor.java
│       ├── RouterProperties.java
│       └── ConfigurableRouteInitializer.java
└── pom.xml
```

## 扩展能力

### 自定义路由策略

实现 `StreamEventRouter` 接口自定义路由逻辑：

```java
@Component
public class CustomEventRouter implements StreamEventRouter {
    // 实现自定义路由逻辑
}
```

### 自定义事件类型

只需创建普通的 POJO 类即可作为事件类型：

```java
public class CustomEvent {
    private String id;
    private String data;
    // getter/setter
}
```

## 测试

```bash
mvn clean test -pl structure-infra-sample/structure-infra-sample-stream -am
```

测试覆盖：
- 事件路由分发
- 业务类型过滤
- SpEL 条件表达式
- 多处理器并发处理
- 路由注册/注销
- 配置驱动路由

## License

Apache License 2.0
