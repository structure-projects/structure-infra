package cn.structure.infra.sample.stream.listener;

import cn.structure.infra.sample.stream.event.OrderEvent;
import cn.structure.infra.stream.annotation.StreamRouteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    public static final AtomicInteger orderCreatedCount = new AtomicInteger(0);
    public static final AtomicInteger orderPaidCount = new AtomicInteger(0);
    public static final AtomicInteger orderCancelledCount = new AtomicInteger(0);
    public static final AtomicInteger highAmountOrderCount = new AtomicInteger(0);
    public static final AtomicReference<OrderEvent> lastOrderEvent = new AtomicReference<>();

    @StreamRouteHandler(eventType = "orderCreated")
    public void handleOrderCreated(OrderEvent event) {
        log.info("[订单创建] orderId={}, orderNo={}, amount={}",
                event.getOrderId(), event.getOrderNo(), event.getAmount());
        orderCreatedCount.incrementAndGet();
        lastOrderEvent.set(event);
    }

    @StreamRouteHandler(eventType = "orderPaid")
    public void handleOrderPaid(OrderEvent event) {
        log.info("[订单支付] orderId={}, amount={}", event.getOrderId(), event.getAmount());
        orderPaidCount.incrementAndGet();
        lastOrderEvent.set(event);
    }

    @StreamRouteHandler(eventType = "orderCancelled")
    public void handleOrderCancelled(OrderEvent event) {
        log.info("[订单取消] orderId={}", event.getOrderId());
        orderCancelledCount.incrementAndGet();
        lastOrderEvent.set(event);
    }

    @StreamRouteHandler(eventType = "orderCreated", condition = "#payload.amount > 1000")
    public void handleHighAmountOrder(OrderEvent event) {
        log.info("[高额订单] orderId={}, amount={}", event.getOrderId(), event.getAmount());
        highAmountOrderCount.incrementAndGet();
    }

}
