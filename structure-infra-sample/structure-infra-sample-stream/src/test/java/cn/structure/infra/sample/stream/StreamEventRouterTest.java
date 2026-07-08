package cn.structure.infra.sample.stream;

import cn.structure.infra.sample.stream.event.OrderEvent;
import cn.structure.infra.sample.stream.event.PaymentEvent;
import cn.structure.infra.stream.event.StreamEvent;
import cn.structure.infra.stream.router.DefaultStreamEventRouterImpl;
import cn.structure.infra.stream.router.StreamEventRouter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class StreamEventRouterTest {

    private StreamEventRouter streamEventRouter;

    @BeforeEach
    public void setUp() {
        streamEventRouter = new DefaultStreamEventRouterImpl();
    }

    @Test
    public void testRouteOrderCreatedEvent() {
        int[] counter = {0};

        streamEventRouter.registerRoute("orderCreated", OrderEvent.class, (payload, event) -> {
            counter[0]++;
        });

        OrderEvent orderEvent = OrderEvent.builder()
                .orderId("ORDER-001")
                .orderNo("ORD-2024-001")
                .status("CREATED")
                .amount(100.0)
                .build();

        StreamEvent<OrderEvent> streamEvent = StreamEvent.of("orderCreated", orderEvent);
        streamEventRouter.route(streamEvent);

        assertEquals(1, counter[0]);
    }

    @Test
    public void testRoutePaymentSuccessEvent() {
        int[] counter = {0};

        streamEventRouter.registerRoute("paymentSuccess", PaymentEvent.class, (payload, event) -> {
            counter[0]++;
        });

        PaymentEvent paymentEvent = PaymentEvent.builder()
                .paymentId("PAY-001")
                .orderId("ORDER-001")
                .paymentStatus("SUCCESS")
                .amount(100.0)
                .build();

        StreamEvent<PaymentEvent> streamEvent = StreamEvent.of("paymentSuccess", paymentEvent);
        streamEventRouter.route(streamEvent);

        assertEquals(1, counter[0]);
    }

    @Test
    public void testRouteMultipleEventTypes() {
        int[] orderCounter = {0};
        int[] paymentCounter = {0};

        streamEventRouter.registerRoute("orderCreated", OrderEvent.class, (payload, event) -> {
            orderCounter[0]++;
        });

        streamEventRouter.registerRoute("paymentSuccess", PaymentEvent.class, (payload, event) -> {
            paymentCounter[0]++;
        });

        OrderEvent orderEvent = OrderEvent.builder()
                .orderId("ORDER-001")
                .orderNo("ORD-2024-001")
                .status("CREATED")
                .amount(100.0)
                .build();

        PaymentEvent paymentEvent = PaymentEvent.builder()
                .paymentId("PAY-001")
                .orderId("ORDER-001")
                .paymentStatus("SUCCESS")
                .amount(100.0)
                .build();

        streamEventRouter.route(StreamEvent.of("orderCreated", orderEvent));
        streamEventRouter.route(StreamEvent.of("paymentSuccess", paymentEvent));

        assertEquals(1, orderCounter[0]);
        assertEquals(1, paymentCounter[0]);
    }

    @Test
    public void testRouteWithCondition() {
        int[] highAmountCounter = {0};
        int[] lowAmountCounter = {0};

        streamEventRouter.registerRoute("orderCreated", OrderEvent.class, "#payload.amount > 1000", (payload, event) -> {
            highAmountCounter[0]++;
        });

        streamEventRouter.registerRoute("orderCreated", OrderEvent.class, "#payload.amount <= 1000", (payload, event) -> {
            lowAmountCounter[0]++;
        });

        OrderEvent highAmountOrder = OrderEvent.builder()
                .orderId("ORDER-001")
                .orderNo("ORD-2024-001")
                .status("CREATED")
                .amount(2000.0)
                .build();

        OrderEvent lowAmountOrder = OrderEvent.builder()
                .orderId("ORDER-002")
                .orderNo("ORD-2024-002")
                .status("CREATED")
                .amount(500.0)
                .build();

        streamEventRouter.route(StreamEvent.of("orderCreated", highAmountOrder));
        streamEventRouter.route(StreamEvent.of("orderCreated", lowAmountOrder));

        assertEquals(1, highAmountCounter[0]);
        assertEquals(1, lowAmountCounter[0]);
    }

    @Test
    public void testRouteWithBusinessType() {
        int[] retailCounter = {0};
        int[] wholesaleCounter = {0};
        int[] allCounter = {0};

        streamEventRouter.registerRoute("orderCreated", "retail", OrderEvent.class, (payload, event) -> {
            retailCounter[0]++;
        });

        streamEventRouter.registerRoute("orderCreated", "wholesale", OrderEvent.class, (payload, event) -> {
            wholesaleCounter[0]++;
        });

        streamEventRouter.registerRoute("orderCreated", OrderEvent.class, (payload, event) -> {
            allCounter[0]++;
        });

        OrderEvent retailOrder = OrderEvent.builder()
                .orderId("ORDER-001")
                .orderNo("ORD-2024-001")
                .status("CREATED")
                .amount(100.0)
                .build();

        OrderEvent wholesaleOrder = OrderEvent.builder()
                .orderId("ORDER-002")
                .orderNo("ORD-2024-002")
                .status("CREATED")
                .amount(5000.0)
                .build();

        streamEventRouter.route(StreamEvent.of("orderCreated", "retail", retailOrder));
        streamEventRouter.route(StreamEvent.of("orderCreated", "wholesale", wholesaleOrder));

        assertEquals(1, retailCounter[0]);
        assertEquals(1, wholesaleCounter[0]);
        assertEquals(2, allCounter[0]);
    }

    @Test
    public void testRouteWithMultipleHandlers() {
        int[] handler1Counter = {0};
        int[] handler2Counter = {0};
        int[] handler3Counter = {0};

        streamEventRouter.registerRoute("orderCreated", OrderEvent.class, (payload, event) -> {
            handler1Counter[0]++;
        });

        streamEventRouter.registerRoute("orderCreated", OrderEvent.class, "#payload.amount > 500", (payload, event) -> {
            handler2Counter[0]++;
        });

        streamEventRouter.registerRoute("orderCreated", OrderEvent.class, "#payload.amount > 1000", (payload, event) -> {
            handler3Counter[0]++;
        });

        OrderEvent highAmountOrder = OrderEvent.builder()
                .orderId("ORDER-001")
                .orderNo("ORD-2024-001")
                .status("CREATED")
                .amount(2000.0)
                .build();

        streamEventRouter.route(StreamEvent.of("orderCreated", highAmountOrder));

        assertEquals(1, handler1Counter[0]);
        assertEquals(1, handler2Counter[0]);
        assertEquals(1, handler3Counter[0]);
    }

    @Test
    public void testUnregisterRoute() {
        int[] counter = {0};

        streamEventRouter.registerRoute("orderCreated", OrderEvent.class, (payload, event) -> {
            counter[0]++;
        });

        assertEquals(1, streamEventRouter.getRoutes("orderCreated").size());

        streamEventRouter.unregisterRoute("orderCreated");

        assertEquals(0, streamEventRouter.getRoutes("orderCreated").size());

        OrderEvent orderEvent = OrderEvent.builder()
                .orderId("ORDER-001")
                .orderNo("ORD-2024-001")
                .status("CREATED")
                .amount(100.0)
                .build();

        streamEventRouter.route(StreamEvent.of("orderCreated", orderEvent));

        assertEquals(0, counter[0]);
    }

    @Test
    public void testUnregisterRouteById() {
        int[] counter1 = {0};
        int[] counter2 = {0};

        streamEventRouter.registerRoute("orderCreated", OrderEvent.class, (payload, event) -> {
            counter1[0]++;
        });

        streamEventRouter.registerRoute("orderCreated", OrderEvent.class, (payload, event) -> {
            counter2[0]++;
        });

        assertEquals(2, streamEventRouter.getRoutes("orderCreated").size());

        String handlerIdToRemove = streamEventRouter.getRoutes("orderCreated").get(0).getHandlerId();
        streamEventRouter.unregisterRoute("orderCreated", handlerIdToRemove);

        assertEquals(1, streamEventRouter.getRoutes("orderCreated").size());

        OrderEvent orderEvent = OrderEvent.builder()
                .orderId("ORDER-001")
                .orderNo("ORD-2024-001")
                .status("CREATED")
                .amount(100.0)
                .build();

        streamEventRouter.route(StreamEvent.of("orderCreated", orderEvent));

        assertEquals(1, counter1[0] + counter2[0]);
    }

    @Test
    public void testGetRoutes() {
        assertTrue(streamEventRouter.getRoutes("orderCreated").isEmpty());

        streamEventRouter.registerRoute("orderCreated", OrderEvent.class, (payload, event) -> {});

        assertEquals(1, streamEventRouter.getRoutes("orderCreated").size());

        streamEventRouter.registerRoute("orderCreated", OrderEvent.class, "#payload.amount > 1000", (payload, event) -> {});

        assertEquals(2, streamEventRouter.getRoutes("orderCreated").size());

        assertTrue(streamEventRouter.getRoutes("nonExistent").isEmpty());
    }

    @Test
    public void testRouteNonExistentEventType() {
        OrderEvent orderEvent = OrderEvent.builder()
                .orderId("ORDER-001")
                .orderNo("ORD-2024-001")
                .status("CREATED")
                .amount(100.0)
                .build();

        assertDoesNotThrow(() -> streamEventRouter.route(StreamEvent.of("nonExistent", orderEvent)));
    }

    @Test
    public void testRouteWithNullEvent() {
        assertDoesNotThrow(() -> streamEventRouter.route(null));
    }

    @Test
    public void testRouteWithNullEventType() {
        OrderEvent orderEvent = OrderEvent.builder()
                .orderId("ORDER-001")
                .orderNo("ORD-2024-001")
                .status("CREATED")
                .amount(100.0)
                .build();

        StreamEvent<OrderEvent> streamEvent = StreamEvent.<OrderEvent>builder()
                .eventId("test-id")
                .eventType(null)
                .payload(orderEvent)
                .build();

        assertDoesNotThrow(() -> streamEventRouter.route(streamEvent));
    }

}
