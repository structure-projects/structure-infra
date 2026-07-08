package cn.structure.infra.sample.stream.listener;

import cn.structure.infra.sample.stream.event.DeliveryEvent;
import cn.structure.infra.stream.annotation.StreamEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DeliveryEventListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryEventListener.class);

    @StreamEventListener(bindingName = "deliveryEvent", destination = "delivery-exchange", group = "delivery-group")
    public void handleDeliveryEvent(DeliveryEvent event) {
        log.info("[配送事件] deliveryId={}, orderId={}, status={}, address={}",
                event.getDeliveryId(), event.getOrderId(), event.getStatus(), event.getAddress());
    }

    @StreamEventListener(bindingName = "deliveryEvent", destination = "delivery-exchange", group = "delivery-group", condition = "#event.status == 'STARTED'")
    public void handleDeliveryStarted(DeliveryEvent event) {
        log.info("[配送开始] deliveryId={}, orderId={}, address={}",
                event.getDeliveryId(), event.getOrderId(), event.getAddress());
    }

}