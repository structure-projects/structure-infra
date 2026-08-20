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

package cn.structure.infra.sample.stream.listener;

import cn.structure.infra.sample.stream.event.OrderEvent;
import cn.structure.infra.stream.annotation.StreamEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    @StreamEventListener(bindingName = "orderEvent", destination = "order-exchange", group = "order-group")
    public void handleOrderEvent(OrderEvent event) {
        log.info("[订单事件] orderId={}, orderNo={}, status={}, amount={}",
                event.getOrderId(), event.getOrderNo(), event.getStatus(), event.getAmount());
    }

    @StreamEventListener(bindingName = "orderEvent", destination = "order-exchange", group = "order-group", condition = "#event.status == 'CREATED'")
    public void handleOrderCreated(OrderEvent event) {
        log.info("[订单创建] orderId={}, orderNo={}, amount={}",
                event.getOrderId(), event.getOrderNo(), event.getAmount());
    }

    @StreamEventListener(bindingName = "orderEvent", destination = "order-exchange", group = "order-group", condition = "#event.status == 'PAID'")
    public void handleOrderPaid(OrderEvent event) {
        log.info("[订单支付] orderId={}, amount={}", event.getOrderId(), event.getAmount());
    }

}