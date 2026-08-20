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

    @StreamEventListener(bindingName = "paymentEvent1", destination = "payment-exchange", group = "payment-group", condition = "#event.paymentStatus == 'SUCCESS'")
    public void handlePaymentSuccess(PaymentEvent event) {
        log.info("[支付成功] paymentId={}, orderId={}, amount={}",
                event.getPaymentId(), event.getOrderId(), event.getAmount());
    }

}