package cn.structure.infra.sample.stream.listener;

import cn.structure.infra.sample.stream.event.PaymentEvent;
import cn.structure.infra.stream.annotation.StreamRouteHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class PaymentEventListener {

    public static final AtomicInteger paymentSuccessCount = new AtomicInteger(0);
    public static final AtomicInteger paymentFailedCount = new AtomicInteger(0);
    public static final AtomicReference<PaymentEvent> lastPaymentEvent = new AtomicReference<>();

    @StreamRouteHandler(eventType = "paymentSuccess")
    public void handlePaymentSuccess(PaymentEvent event) {
        log.info("[支付成功] paymentId={}, orderId={}, amount={}",
                event.getPaymentId(), event.getOrderId(), event.getAmount());
        paymentSuccessCount.incrementAndGet();
        lastPaymentEvent.set(event);
    }

    @StreamRouteHandler(eventType = "paymentFailed")
    public void handlePaymentFailed(PaymentEvent event) {
        log.info("[支付失败] paymentId={}, orderId={}, status={}",
                event.getPaymentId(), event.getOrderId(), event.getPaymentStatus());
        paymentFailedCount.incrementAndGet();
        lastPaymentEvent.set(event);
    }

}
