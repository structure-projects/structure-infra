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