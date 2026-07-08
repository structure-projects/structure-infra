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

@Configuration
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