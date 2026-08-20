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

package cn.structure.infra.sample.stream.controller;

import cn.structure.infra.sample.stream.event.DeliveryEvent;
import cn.structure.infra.sample.stream.event.OrderEvent;
import cn.structure.infra.sample.stream.event.PaymentEvent;
import cn.structure.infra.stream.manager.StreamEventManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stream")
public class StreamController {

    private final StreamEventManager streamEventManager;

    public StreamController(StreamEventManager streamEventManager) {
        this.streamEventManager = streamEventManager;
    }

    @PostMapping("/order")
    public String sendOrderEvent(@RequestBody OrderEvent event) {
        streamEventManager.publish("orderEvent", event);
        return "Order event sent: " + event.getOrderId();
    }

    @PostMapping("/order/create")
    public String sendOrderCreatedEvent() {
        OrderEvent event = OrderEvent.builder()
                .orderId("ORDER-" + System.currentTimeMillis())
                .orderNo("ORD-" + System.currentTimeMillis())
                .status("CREATED")
                .amount(100.0)
                .build();
        streamEventManager.publish("orderEvent", event);
        return "Order created event sent: " + event.getOrderId();
    }

    @PostMapping("/order/pay")
    public String sendOrderPaidEvent(@RequestBody OrderEvent event) {
        event.setStatus("PAID");
        streamEventManager.publish("orderEvent", event);
        return "Order paid event sent: " + event.getOrderId();
    }

    @PostMapping("/payment")
    public String sendPaymentEvent(@RequestBody PaymentEvent event) {
        streamEventManager.publish("paymentEvent", event);
        return "Payment event sent: " + event.getPaymentId();
    }

    @PostMapping("/payment/success")
    public String sendPaymentSuccessEvent(@RequestBody PaymentEvent event) {
        event.setPaymentStatus("SUCCESS");
        streamEventManager.publish("paymentEvent", event);
        return "Payment success event sent: " + event.getPaymentId();
    }

    @PostMapping("/delivery")
    public String sendDeliveryEvent(@RequestBody DeliveryEvent event) {
        streamEventManager.publish("deliveryEvent", event);
        return "Delivery event sent: " + event.getDeliveryId();
    }

    @PostMapping("/delivery/start")
    public String sendDeliveryStartedEvent(@RequestBody DeliveryEvent event) {
        event.setStatus("STARTED");
        streamEventManager.publish("deliveryEvent", event);
        return "Delivery started event sent: " + event.getDeliveryId();
    }

}