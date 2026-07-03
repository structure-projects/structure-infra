package cn.structure.infra.sample.stream.listener;

import cn.structure.infra.sample.stream.event.PaymentEvent;
import cn.structure.infra.stream.annotation.StreamEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    @StreamEventListener(bindingName = "paymentEvent", destination = "payment-exchange", group = "payment-group")
    public void handlePaymentEvent(PaymentEvent event) {
        log.info("[支付事件] paymentId={}, orderId={}, status={}, amount={}",
                event.getPaymentId(), event.getOrderId(), event.getPaymentStatus(), event.getAmount());
    }

    @StreamEventListener(bindingName = "paymentEvent", destination = "payment-exchange", group = "payment-group", condition = "#event.paymentStatus == 'SUCCESS'")
    public void handlePaymentSuccess(PaymentEvent event) {
        log.info("[支付成功] paymentId={}, orderId={}, amount={}",
                event.getPaymentId(), event.getOrderId(), event.getAmount());
    }

}