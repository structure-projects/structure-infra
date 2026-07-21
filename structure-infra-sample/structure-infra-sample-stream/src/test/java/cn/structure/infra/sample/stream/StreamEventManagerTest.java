package cn.structure.infra.sample.stream;

import cn.structure.infra.sample.stream.event.OrderEvent;
import cn.structure.infra.sample.stream.event.PaymentEvent;
import cn.structure.infra.stream.manager.DefaultStreamEventManagerImpl;
import cn.structure.infra.stream.manager.StreamEventManager;
import cn.structure.infra.stream.properties.StreamProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@Slf4j
public class StreamEventManagerTest {

    private StreamEventManager streamEventManager;

    private StreamProperties streamProperties;

    @BeforeEach
    public void setUp() {
        streamProperties = new StreamProperties();
        StreamProperties.Binding orderBinding = new StreamProperties.Binding();
        orderBinding.setDestination("order-exchange");
        orderBinding.setContentType("application/json");
        orderBinding.setGroup("order-group");
        streamProperties.getBindings().put("orderEvent", orderBinding);

        StreamProperties.Binding paymentBinding = new StreamProperties.Binding();
        paymentBinding.setDestination("payment-exchange");
        paymentBinding.setContentType("application/json");
        paymentBinding.setGroup("payment-group");
        streamProperties.getBindings().put("paymentEvent", paymentBinding);

        StreamBridge mockStreamBridge = mock(StreamBridge.class);
        ConfigurableEnvironment mockEnvironment = mock(ConfigurableEnvironment.class);
        streamEventManager = new DefaultStreamEventManagerImpl(mockStreamBridge, streamProperties, mockEnvironment);
    }

    @Test
    public void testStreamPropertiesConfiguration() {
        assertNotNull(streamProperties);
        assertTrue(streamProperties.isEnabled());
        assertNotNull(streamProperties.getBindings());
        assertTrue(streamProperties.getBindings().containsKey("orderEvent"));
        assertTrue(streamProperties.getBindings().containsKey("paymentEvent"));

        StreamProperties.Binding orderBinding = streamProperties.getBindings().get("orderEvent");
        assertEquals("order-exchange", orderBinding.getDestination());
        assertEquals("application/json", orderBinding.getContentType());
        assertEquals("order-group", orderBinding.getGroup());

        StreamProperties.Binding paymentBinding = streamProperties.getBindings().get("paymentEvent");
        assertEquals("payment-exchange", paymentBinding.getDestination());
        assertEquals("application/json", paymentBinding.getContentType());
        assertEquals("payment-group", paymentBinding.getGroup());
    }

    @Test
    public void testStreamEventManagerBeanExists() {
        assertNotNull(streamEventManager);
    }

    @Test
    public void testRegisterListener() {
        assertFalse(streamEventManager.isListenerRegistered("orderEvent"));

        streamEventManager.registerListener("orderEvent", OrderEvent.class, event -> {});

        assertTrue(streamEventManager.isListenerRegistered("orderEvent"), "orderEvent listener should be registered");
    }

    @Test
    public void testUnregisterListener() {
        streamEventManager.registerListener("orderEvent", OrderEvent.class, event -> {});
        assertTrue(streamEventManager.isListenerRegistered("orderEvent"));

        streamEventManager.unregisterListener("orderEvent");

        assertFalse(streamEventManager.isListenerRegistered("orderEvent"), "orderEvent listener should be unregistered");
    }

    @Test
    public void testRegisterDynamicListener() {
        String testBindingName = "testDynamicEvent";
        int[] counter = {0};

        streamEventManager.registerListener(testBindingName, "test-destination", "test-group", OrderEvent.class, event -> {
            log.info("Dynamic listener received event: {}", event);
            counter[0]++;
        });

        assertTrue(streamEventManager.isListenerRegistered(testBindingName));

        streamEventManager.unregisterListener(testBindingName);

        assertFalse(streamEventManager.isListenerRegistered(testBindingName));
    }

    @Test
    public void testPublishEventWithRegisteredBinding() {
        OrderEvent event = OrderEvent.builder()
                .orderId("ORDER-001")
                .orderNo("ORD-2024-001")
                .status("CREATED")
                .amount(100.0)
                .build();

        assertDoesNotThrow(() -> streamEventManager.publish("orderEvent", event));
    }

    @Test
    public void testPublishEventWithDestination() {
        OrderEvent event = OrderEvent.builder()
                .orderId("ORDER-002")
                .orderNo("ORD-2024-002")
                .status("PAID")
                .amount(200.0)
                .build();

        assertDoesNotThrow(() -> streamEventManager.publish("orderEvent", "order-exchange", event));
    }

    @Test
    public void testPublishPaymentEvent() {
        PaymentEvent event = PaymentEvent.builder()
                .paymentId("PAY-001")
                .orderId("ORDER-001")
                .paymentStatus("SUCCESS")
                .amount(100.0)
                .build();

        assertDoesNotThrow(() -> streamEventManager.publish("paymentEvent", event));
    }

    @Test
    public void testPublishEventWithNonExistentBinding() {
        OrderEvent event = OrderEvent.builder()
                .orderId("ORDER-003")
                .orderNo("ORD-2024-003")
                .status("CANCELLED")
                .amount(50.0)
                .build();

        assertThrows(IllegalArgumentException.class, () -> streamEventManager.publish("nonExistentBinding", event));
    }

    @Test
    public void testIsListenerRegisteredForNonExistentBinding() {
        assertFalse(streamEventManager.isListenerRegistered("nonExistentBinding"));
    }

    @Test
    public void testRegisterAndUnregisterCycle() {
        String bindingName = "testCycleEvent";

        streamEventManager.registerListener(bindingName, "test-destination", "test-group", OrderEvent.class, event -> {});
        assertTrue(streamEventManager.isListenerRegistered(bindingName));

        streamEventManager.unregisterListener(bindingName);
        assertFalse(streamEventManager.isListenerRegistered(bindingName));

        streamEventManager.registerListener(bindingName, "test-destination", "test-group", PaymentEvent.class, event -> {});
        assertTrue(streamEventManager.isListenerRegistered(bindingName));

        streamEventManager.unregisterListener(bindingName);
        assertFalse(streamEventManager.isListenerRegistered(bindingName));
    }

    @Test
    public void testRegisterListenerWithAllParameters() {
        String bindingName = "testFullParams";

        streamEventManager.registerListener(bindingName, "test-destination", "test-group", OrderEvent.class, event -> {});

        assertTrue(streamEventManager.isListenerRegistered(bindingName));

        streamEventManager.unregisterListener(bindingName);

        assertFalse(streamEventManager.isListenerRegistered(bindingName));
    }

    @Test
    public void testMultipleListeners() {
        streamEventManager.registerListener("listener1", "test-destination", "test-group", OrderEvent.class, event -> {});
        streamEventManager.registerListener("listener2", "test-destination", "test-group", PaymentEvent.class, event -> {});
        streamEventManager.registerListener("listener3", "test-destination", "test-group", OrderEvent.class, event -> {});

        assertTrue(streamEventManager.isListenerRegistered("listener1"));
        assertTrue(streamEventManager.isListenerRegistered("listener2"));
        assertTrue(streamEventManager.isListenerRegistered("listener3"));

        streamEventManager.unregisterListener("listener2");

        assertTrue(streamEventManager.isListenerRegistered("listener1"));
        assertFalse(streamEventManager.isListenerRegistered("listener2"));
        assertTrue(streamEventManager.isListenerRegistered("listener3"));

        streamEventManager.unregisterListener("listener1");
        streamEventManager.unregisterListener("listener3");

        assertFalse(streamEventManager.isListenerRegistered("listener1"));
        assertFalse(streamEventManager.isListenerRegistered("listener3"));
    }

    @Test
    public void testMultipleHandlersOnSameBinding() {
        String bindingName = "orderEvent";
        int[] counter1 = {0};
        int[] counter2 = {0};

        streamEventManager.registerListener(bindingName, "order-exchange", "order-group", OrderEvent.class, event -> {
            counter1[0]++;
        });

        streamEventManager.registerListener(bindingName, "order-exchange", "order-group", OrderEvent.class, event -> {
            counter2[0]++;
        });

        assertTrue(streamEventManager.isListenerRegistered(bindingName));
        assertEquals(2, streamEventManager.getListeners(bindingName).size());

        OrderEvent event = OrderEvent.builder()
                .orderId("ORDER-001")
                .orderNo("ORD-2024-001")
                .status("CREATED")
                .amount(100.0)
                .build();

        streamEventManager.dispatch(bindingName, event);

        assertEquals(1, counter1[0]);
        assertEquals(1, counter2[0]);
    }

    @Test
    public void testConditionBasedRouting() {
        String bindingName = "orderEvent";
        int[] createdCounter = {0};
        int[] paidCounter = {0};
        int[] allCounter = {0};

        streamEventManager.registerListener(bindingName, "order-exchange", "order-group", OrderEvent.class, "#event.status == 'CREATED'", event -> {
            createdCounter[0]++;
        });

        streamEventManager.registerListener(bindingName, "order-exchange", "order-group", OrderEvent.class, "#event.status == 'PAID'", event -> {
            paidCounter[0]++;
        });

        streamEventManager.registerListener(bindingName, "order-exchange", "order-group", OrderEvent.class, event -> {
            allCounter[0]++;
        });

        OrderEvent createdEvent = OrderEvent.builder()
                .orderId("ORDER-001")
                .orderNo("ORD-2024-001")
                .status("CREATED")
                .amount(100.0)
                .build();

        OrderEvent paidEvent = OrderEvent.builder()
                .orderId("ORDER-002")
                .orderNo("ORD-2024-002")
                .status("PAID")
                .amount(200.0)
                .build();

        streamEventManager.dispatch(bindingName, createdEvent);

        assertEquals(1, createdCounter[0]);
        assertEquals(0, paidCounter[0]);
        assertEquals(1, allCounter[0]);

        streamEventManager.dispatch(bindingName, paidEvent);

        assertEquals(1, createdCounter[0]);
        assertEquals(1, paidCounter[0]);
        assertEquals(2, allCounter[0]);
    }

    @Test
    public void testDispatchToMultipleHandlersWithDifferentConditions() {
        String bindingName = "orderEvent";
        int[] highAmountCounter = {0};
        int[] lowAmountCounter = {0};

        streamEventManager.registerListener(bindingName, "order-exchange", "order-group", OrderEvent.class, "#event.amount > 100", event -> {
            highAmountCounter[0]++;
        });

        streamEventManager.registerListener(bindingName, "order-exchange", "order-group", OrderEvent.class, "#event.amount <= 100", event -> {
            lowAmountCounter[0]++;
        });

        OrderEvent highAmountEvent = OrderEvent.builder()
                .orderId("ORDER-001")
                .orderNo("ORD-2024-001")
                .status("CREATED")
                .amount(150.0)
                .build();

        OrderEvent lowAmountEvent = OrderEvent.builder()
                .orderId("ORDER-002")
                .orderNo("ORD-2024-002")
                .status("CREATED")
                .amount(50.0)
                .build();

        streamEventManager.dispatch(bindingName, highAmountEvent);

        assertEquals(1, highAmountCounter[0]);
        assertEquals(0, lowAmountCounter[0]);

        streamEventManager.dispatch(bindingName, lowAmountEvent);

        assertEquals(1, highAmountCounter[0]);
        assertEquals(1, lowAmountCounter[0]);
    }

    @Test
    public void testUnregisterListenerById() {
        String bindingName = "orderEvent";
        int[] counter1 = {0};
        int[] counter2 = {0};

        streamEventManager.registerListener(bindingName, "order-exchange", "order-group", OrderEvent.class, event -> {
            log.info("Listener 1 triggered");
            counter1[0]++;
        });

        streamEventManager.registerListener(bindingName, "order-exchange", "order-group", OrderEvent.class, event -> {
            counter2[0]++;
        });

        assertEquals(2, streamEventManager.getListeners(bindingName).size());

        String listenerIdToRemove = streamEventManager.getListeners(bindingName).get(0).getListenerId();
        streamEventManager.unregisterListener(bindingName, listenerIdToRemove);

        assertEquals(1, streamEventManager.getListeners(bindingName).size());
        assertTrue(streamEventManager.isListenerRegistered(bindingName));

        OrderEvent event = OrderEvent.builder()
                .orderId("ORDER-001")
                .orderNo("ORD-2024-001")
                .status("CREATED")
                .amount(100.0)
                .build();

        streamEventManager.dispatch(bindingName, event);

        assertEquals(1, counter1[0] + counter2[0]);
    }

    @Test
    public void testGetListeners() {
        String bindingName = "orderEvent";

        assertTrue(streamEventManager.getListeners(bindingName).isEmpty());

        streamEventManager.registerListener(bindingName, "order-exchange", "order-group", OrderEvent.class, event -> {});

        assertEquals(1, streamEventManager.getListeners(bindingName).size());

        streamEventManager.registerListener(bindingName, "order-exchange", "order-group", OrderEvent.class, "#event.status == 'PAID'", event -> {});

        assertEquals(2, streamEventManager.getListeners(bindingName).size());

        assertTrue(streamEventManager.getListeners("nonExistent").isEmpty());
    }

}
