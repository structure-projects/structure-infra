package cn.structure.infra.sample.stream.listener;

import cn.structure.infra.sample.stream.event.DeliveryEvent;
import cn.structure.infra.stream.annotation.StreamEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class DeliveryEventListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryEventListener.class);

    public static final AtomicInteger deliveryCreatedCount = new AtomicInteger(0);
    public static final AtomicInteger deliveryCompletedCount = new AtomicInteger(0);
    public static final AtomicReference<DeliveryEvent> lastDeliveryEvent = new AtomicReference<>();

    @StreamEventListener(bindingName = "deliveryEvent", destination = "delivery-exchange", group = "delivery-group")
    public void handleDeliveryCreated(DeliveryEvent event) {
        log.info("[配送创建] deliveryId={}, orderId={}, status={}",
                event.getDeliveryId(), event.getOrderId(), event.getStatus());
        if ("CREATED".equals(event.getStatus())) {
            deliveryCreatedCount.incrementAndGet();
        } else if ("COMPLETED".equals(event.getStatus())) {
            deliveryCompletedCount.incrementAndGet();
        }
        lastDeliveryEvent.set(event);
    }

}
