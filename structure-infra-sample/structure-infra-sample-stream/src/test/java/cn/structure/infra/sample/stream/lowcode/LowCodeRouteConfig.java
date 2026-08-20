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

package cn.structure.infra.sample.stream.lowcode;

import cn.structure.infra.sample.stream.event.OrderEvent;
import cn.structure.infra.sample.stream.event.PaymentEvent;
import cn.structure.infra.stream.event.StreamEvent;
import cn.structure.infra.stream.router.StreamEventRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class LowCodeRouteConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LowCodeRouteConfig.class);

    private final StreamEventRouter eventRouter;

    public LowCodeRouteConfig(StreamEventRouter eventRouter) {
        this.eventRouter = eventRouter;
    }

    @Override
    public void run(String... args) {
        log.info("========== 低代码路由注册示例 ==========");

        registerOrderRoutes();
        registerPaymentRoutes();

        log.info("========== 低代码路由注册完成 ==========");

        testLowCodeRoutes();
    }

    private void registerOrderRoutes() {
        eventRouter.registerRoute("orderCreated", OrderEvent.class, (payload, event) -> {
            log.info("[低代码-订单创建] orderId={}, amount={}", payload.getOrderId(), payload.getAmount());
        });

        eventRouter.registerRoute("orderCreated", OrderEvent.class, "#payload.amount > 1000", (payload, event) -> {
            log.info("[低代码-高额订单] orderId={}, amount={}, 触发风控检查", payload.getOrderId(), payload.getAmount());
        });

        eventRouter.registerRoute("orderPaid", OrderEvent.class, (payload, event) -> {
            log.info("[低代码-订单支付] orderId={}, status={}", payload.getOrderId(), payload.getStatus());
        });

        eventRouter.registerRoute("orderCancelled", OrderEvent.class, (payload, event) -> {
            log.info("[低代码-订单取消] orderId={}", payload.getOrderId());
        });
    }

    private void registerPaymentRoutes() {
        eventRouter.registerRoute("paymentSuccess", PaymentEvent.class, (payload, event) -> {
            log.info("[低代码-支付成功] paymentId={}, orderId={}", payload.getPaymentId(), payload.getOrderId());
        });

        eventRouter.registerRoute("paymentFailed", PaymentEvent.class, (payload, event) -> {
            log.info("[低代码-支付失败] paymentId={}, status={}", payload.getPaymentId(), payload.getPaymentStatus());
        });
    }

    private void testLowCodeRoutes() {
        log.info("========== 低代码路由测试 ==========");

        OrderEvent normalOrder = OrderEvent.builder()
                .orderId("LC-001")
                .orderNo("LC-ORD-2024-001")
                .status("CREATED")
                .amount(500.0)
                .build();

        OrderEvent highAmountOrder = OrderEvent.builder()
                .orderId("LC-002")
                .orderNo("LC-ORD-2024-002")
                .status("CREATED")
                .amount(2000.0)
                .build();

        eventRouter.route(StreamEvent.of("orderCreated", normalOrder));
        eventRouter.route(StreamEvent.of("orderCreated", highAmountOrder));

        PaymentEvent paymentEvent = PaymentEvent.builder()
                .paymentId("LC-PAY-001")
                .orderId("LC-001")
                .paymentStatus("SUCCESS")
                .amount(500.0)
                .build();

        eventRouter.route(StreamEvent.of("paymentSuccess", paymentEvent));

        log.info("========== 低代码路由测试完成 ==========");
    }
}
