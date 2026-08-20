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

package cn.structure.infra.sample.stream.controller;

import cn.structure.infra.sample.stream.event.DeliveryEvent;
import cn.structure.infra.sample.stream.event.OrderEvent;
import cn.structure.infra.sample.stream.event.PaymentEvent;
import cn.structure.infra.stream.manager.StreamEventManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class StreamControllerTest {

    @Mock
    private StreamEventManager streamEventManager;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        StreamController controller = new StreamController(streamEventManager);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testSendOrderEvent() throws Exception {
        OrderEvent event = OrderEvent.builder()
                .orderId("ORDER-001")
                .orderNo("ORD-2024-001")
                .status("CREATED")
                .amount(100.0)
                .build();

        mockMvc.perform(post("/api/stream/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(content().string("Order event sent: ORDER-001"));

        verify(streamEventManager).publish(eq("orderEvent"), any(OrderEvent.class));
    }

    @Test
    public void testSendOrderCreatedEvent() throws Exception {
        mockMvc.perform(post("/api/stream/order/create"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("Order created event sent: ORDER-")));

        verify(streamEventManager).publish(eq("orderEvent"), any(OrderEvent.class));
    }

    @Test
    public void testSendOrderPaidEvent() throws Exception {
        OrderEvent event = OrderEvent.builder()
                .orderId("ORDER-001")
                .orderNo("ORD-2024-001")
                .amount(200.0)
                .build();

        mockMvc.perform(post("/api/stream/order/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(content().string("Order paid event sent: ORDER-001"));

        verify(streamEventManager).publish(eq("orderEvent"), any(OrderEvent.class));
    }

    @Test
    public void testSendPaymentEvent() throws Exception {
        PaymentEvent event = PaymentEvent.builder()
                .paymentId("PAY-001")
                .orderId("ORDER-001")
                .paymentStatus("PENDING")
                .amount(100.0)
                .build();

        mockMvc.perform(post("/api/stream/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(content().string("Payment event sent: PAY-001"));

        verify(streamEventManager).publish(eq("paymentEvent"), any(PaymentEvent.class));
    }

    @Test
    public void testSendPaymentSuccessEvent() throws Exception {
        PaymentEvent event = PaymentEvent.builder()
                .paymentId("PAY-001")
                .orderId("ORDER-001")
                .amount(100.0)
                .build();

        mockMvc.perform(post("/api/stream/payment/success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(content().string("Payment success event sent: PAY-001"));

        verify(streamEventManager).publish(eq("paymentEvent"), any(PaymentEvent.class));
    }

    @Test
    public void testSendDeliveryEvent() throws Exception {
        DeliveryEvent event = DeliveryEvent.builder()
                .deliveryId("DEL-001")
                .orderId("ORDER-001")
                .status("PREPARING")
                .address("北京市朝阳区")
                .build();

        mockMvc.perform(post("/api/stream/delivery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(content().string("Delivery event sent: DEL-001"));

        verify(streamEventManager).publish(eq("deliveryEvent"), any(DeliveryEvent.class));
    }

    @Test
    public void testSendDeliveryStartedEvent() throws Exception {
        DeliveryEvent event = DeliveryEvent.builder()
                .deliveryId("DEL-001")
                .orderId("ORDER-001")
                .address("北京市朝阳区")
                .build();

        mockMvc.perform(post("/api/stream/delivery/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(content().string("Delivery started event sent: DEL-001"));

        verify(streamEventManager).publish(eq("deliveryEvent"), any(DeliveryEvent.class));
    }

}