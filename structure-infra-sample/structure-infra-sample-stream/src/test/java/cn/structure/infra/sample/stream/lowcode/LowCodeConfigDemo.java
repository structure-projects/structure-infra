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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component("lowCodeHandler")
public class LowCodeConfigDemo {

    private static final Logger log = LoggerFactory.getLogger(LowCodeConfigDemo.class);

    public static final AtomicInteger configOrderCreatedCount = new AtomicInteger(0);
    public static final AtomicInteger configPaymentSuccessCount = new AtomicInteger(0);

    public void onOrderCreated(OrderEvent event) {
        log.info("[配置驱动] 订单创建处理: orderId={}, amount={}", event.getOrderId(), event.getAmount());
        configOrderCreatedCount.incrementAndGet();
    }

    public void onPaymentSuccess(PaymentEvent event) {
        log.info("[配置驱动] 支付成功处理: paymentId={}, orderId={}", event.getPaymentId(), event.getOrderId());
        configPaymentSuccessCount.incrementAndGet();
    }

    public void onHighAmountOrder(OrderEvent event) {
        log.info("[配置驱动-高额订单] 订单金额超过阈值: orderId={}, amount={}", event.getOrderId(), event.getAmount());
    }
}
