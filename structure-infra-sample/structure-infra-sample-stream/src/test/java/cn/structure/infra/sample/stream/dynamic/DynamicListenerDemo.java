/*
Copyright 2023 Structure Projects

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	`http://www.apache.org/licenses/LICENSE-2.0`

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package cn.structure.infra.sample.stream.dynamic;

import cn.structure.infra.sample.stream.event.OrderEvent;
import cn.structure.infra.sample.stream.event.PaymentEvent;
import cn.structure.infra.stream.event.StreamEvent;
import cn.structure.infra.stream.handler.StreamEventHandler;
import cn.structure.infra.stream.manager.ListenerRegistration;
import cn.structure.infra.stream.manager.StreamEventManager;
import cn.structure.infra.stream.router.StreamEventRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DynamicListenerDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DynamicListenerDemo.class);

    private final StreamEventManager eventManager;
    private final StreamEventRouter eventRouter;

    public static final AtomicInteger dynamicOrderCount = new AtomicInteger(0);
    public static final AtomicInteger dynamicPaymentCount = new AtomicInteger(0);
    public static final AtomicInteger dynamicGenericCount = new AtomicInteger(0);

    public DynamicListenerDemo(StreamEventManager eventManager, StreamEventRouter eventRouter) {
        this.eventManager = eventManager;
        this.eventRouter = eventRouter;
    }

    @Override
    public void run(String... args) {
        log.info("========== 运行时动态注册监听器示例 ==========");

        registerDynamicListeners();
        registerDynamicRoutes();

        testDynamicListeners();

        log.info("========== 动态注册监听器示例完成 ==========");
    }

    private void registerDynamicListeners() {
        log.info("\n--- 方式一：通过 StreamEventManager 注册监听器 ---");

        eventManager.registerBinding("dynamicOrder", "dynamic-order-exchange", "dynamic-group");

        eventManager.registerListener("dynamicOrder", OrderEvent.class, new OrderEventHandler());

        eventManager.registerListener("dynamicOrder", OrderEvent.class, event -> {
            log.info("[Lambda方式] 动态监听订单: orderId={}", event.getOrderId());
            dynamicOrderCount.incrementAndGet();
        });

        eventManager.registerListener("dynamicOrder", OrderEvent.class, "#payload.amount > 500", event -> {
            log.info("[Lambda+条件] 动态监听大额订单: orderId={}, amount={}", event.getOrderId(), event.getAmount());
        });

        eventManager.registerListener("dynamicPayment", "dynamic-payment-exchange", "payment-group",
                PaymentEvent.class, new PaymentEventHandler());
    }

    private void registerDynamicRoutes() {
        log.info("\n--- 方式二：通过 StreamEventRouter 注册路由 ---");

        eventRouter.registerRoute("dynamicOrderCreated", OrderEvent.class, new DynamicRouteHandler());

        eventRouter.registerRoute("dynamicPaymentSuccess", PaymentEvent.class, (payload, event) -> {
            log.info("[Lambda路由] 动态路由支付成功: paymentId={}", payload.getPaymentId());
            dynamicPaymentCount.incrementAndGet();
        });

        eventRouter.registerRoute("dynamicGeneric", Object.class, (payload, event) -> {
            log.info("[通用路由] 动态路由通用事件: payload={}", payload);
            dynamicGenericCount.incrementAndGet();
        });

        eventRouter.registerRoute("dynamicWithBusiness", "retail", OrderEvent.class, (payload, event) -> {
            log.info("[业务路由] 动态路由零售订单: orderId={}, businessType={}",
                    payload.getOrderId(), event.getBusinessType());
        });
    }

    private void testDynamicListeners() {
        log.info("\n--- 测试动态注册的监听器 ---");

        OrderEvent orderEvent = OrderEvent.builder()
                .orderId("DYN-001")
                .orderNo("DYN-ORD-2024-001")
                .status("CREATED")
                .amount(800.0)
                .build();

        eventManager.dispatch("dynamicOrder", orderEvent);

        eventRouter.route(StreamEvent.of("dynamicOrderCreated", orderEvent));

        PaymentEvent paymentEvent = PaymentEvent.builder()
                .paymentId("DYN-PAY-001")
                .orderId("DYN-001")
                .paymentStatus("SUCCESS")
                .amount(800.0)
                .build();

        eventRouter.route(StreamEvent.of("dynamicPaymentSuccess", paymentEvent));

        eventRouter.route(StreamEvent.of("dynamicGeneric", "test-message"));

        eventRouter.route(StreamEvent.<OrderEvent>builder()
                .eventType("dynamicWithBusiness")
                .businessType("retail")
                .payload(orderEvent)
                .build());

        logStats();
    }

    private void logStats() {
        log.info("\n--- 统计信息 ---");
        log.info("StreamEventManager 监听器数量:");
        List<ListenerRegistration<?>> orderListeners = eventManager.getListeners("dynamicOrder");
        log.info("  - dynamicOrder: {} 个监听器", orderListeners.size());

        log.info("StreamEventRouter 路由数量:");
        log.info("  - dynamicOrderCreated: {} 个路由", eventRouter.getRoutes("dynamicOrderCreated").size());
        log.info("  - dynamicPaymentSuccess: {} 个路由", eventRouter.getRoutes("dynamicPaymentSuccess").size());

        log.info("处理计数:");
        log.info("  - 动态订单计数: {}", dynamicOrderCount.get());
        log.info("  - 动态支付计数: {}", dynamicPaymentCount.get());
        log.info("  - 动态通用计数: {}", dynamicGenericCount.get());
    }

    public static class OrderEventHandler implements StreamEventHandler<OrderEvent> {
        @Override
        public void handle(OrderEvent event) {
            log.info("[实现类方式] 动态监听订单: orderId={}, status={}", event.getOrderId(), event.getStatus());
            dynamicOrderCount.incrementAndGet();
        }
    }

    public static class PaymentEventHandler implements StreamEventHandler<PaymentEvent> {
        @Override
        public void handle(PaymentEvent event) {
            log.info("[实现类方式] 动态监听支付: paymentId={}, status={}", event.getPaymentId(), event.getPaymentStatus());
            dynamicPaymentCount.incrementAndGet();
        }
    }

    public static class DynamicRouteHandler implements StreamEventRouter.StreamRouteHandler<OrderEvent> {
        @Override
        public void handle(OrderEvent payload, StreamEvent<OrderEvent> event) {
            log.info("[实现类路由] 动态路由订单: orderId={}, eventType={}", payload.getOrderId(), event.getEventType());
            dynamicOrderCount.incrementAndGet();
        }
    }
}
