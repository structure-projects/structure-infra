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
