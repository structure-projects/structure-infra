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

package cn.structure.infra.sample.stream.consumer;

import cn.structure.infra.sample.stream.event.DeliveryEvent;
import cn.structure.infra.sample.stream.event.OrderEvent;
import cn.structure.infra.sample.stream.event.PaymentEvent;
import cn.structure.infra.stream.manager.StreamEventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Configuration(proxyBeanMethods = false)
public class StreamMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(StreamMessageConsumer.class);

    private final StreamEventManager streamEventManager;

    public StreamMessageConsumer(StreamEventManager streamEventManager) {
        this.streamEventManager = streamEventManager;
    }

    @Bean
    public Consumer<Message<OrderEvent>> orderEvent() {
        return message -> {
            OrderEvent event = message.getPayload();
            log.info("[消费者] 收到订单事件: {}", event);
            streamEventManager.dispatch("orderEvent", event);
        };
    }

    @Bean
    public Consumer<Message<PaymentEvent>> paymentEvent() {
        return message -> {
            PaymentEvent event = message.getPayload();
            log.info("[消费者] 收到支付事件: {}", event);
            streamEventManager.dispatch("paymentEvent", event);
        };
    }

    @Bean
    public Consumer<Message<DeliveryEvent>> deliveryEvent() {
        return message -> {
            DeliveryEvent event = message.getPayload();
            log.info("[消费者] 收到配送事件: {}", event);
            streamEventManager.dispatch("deliveryEvent", event);
        };
    }

}