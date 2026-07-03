package cn.structure.infra.schedule;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SpringTaskSchedulerAdapterTest {

    private SpringTaskSchedulerAdapter adapter;
    private LocalThreadTaskScheduler localScheduler;
    private DefaultTaskHandlerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DefaultTaskHandlerRegistry();
        localScheduler = new LocalThreadTaskScheduler(2, registry);
        adapter = new SpringTaskSchedulerAdapter(localScheduler, registry);
    }

    @AfterEach
    void tearDown() {
        localScheduler.getAllTasks().forEach(task -> localScheduler.remove(task.getTaskId()));
    }

    @Test
    void testScheduleAtFixedRateWithDuration() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);

        var future = adapter.scheduleAtFixedRate(() -> counter.incrementAndGet(), Duration.ofMillis(100));

        Thread.sleep(500);

        assertTrue(counter.get() >= 4, "Task should execute at least 4 times");

        assertTrue(future.cancel(false));
        assertTrue(future.isDone());
    }

    @Test
    void testScheduleAtFixedRateWithStartTime() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);

        Instant startTime = Instant.now().plusMillis(100);
        var future = adapter.scheduleAtFixedRate(() -> counter.incrementAndGet(), startTime, Duration.ofMillis(100));

        Thread.sleep(600);

        assertTrue(counter.get() >= 4, "Task should execute at least 4 times");

        assertTrue(future.cancel(false));
    }

    @Test
    void testScheduleWithFixedDelay() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);

        var future = adapter.scheduleWithFixedDelay(() -> counter.incrementAndGet(), Duration.ofMillis(100));

        Thread.sleep(500);

        assertTrue(counter.get() >= 4, "Task should execute at least 4 times");

        assertTrue(future.cancel(false));
    }

    @Test
    void testScheduleWithFixedDelayAndStartTime() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);

        Instant startTime = Instant.now().plusMillis(100);
        var future = adapter.scheduleWithFixedDelay(() -> counter.incrementAndGet(), startTime, Duration.ofMillis(100));

        Thread.sleep(600);

        assertTrue(counter.get() >= 4, "Task should execute at least 4 times");

        assertTrue(future.cancel(false));
    }

    @Test
    void testScheduleWithInstant() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);

        Instant startTime = Instant.now().plusMillis(50);
        var future = adapter.schedule(() -> counter.incrementAndGet(), startTime);

        Thread.sleep(200);

        assertEquals(1, counter.get(), "Task should execute once after delay");

        assertTrue(future.cancel(false));
    }

    @Test
    void testScheduleWithTrigger() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);

        var future = adapter.schedule(() -> counter.incrementAndGet(), triggerContext -> Instant.now().plusMillis(100));

        Thread.sleep(3500);

        assertTrue(counter.get() >= 3, "Task should execute at least 3 times");

        assertTrue(future.cancel(false));
    }

    @Test
    void testCancelTask() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);

        var future = adapter.scheduleAtFixedRate(() -> counter.incrementAndGet(), Duration.ofMillis(50));

        Thread.sleep(200);
        int countBeforeCancel = counter.get();

        assertTrue(future.cancel(false));

        Thread.sleep(200);

        assertEquals(countBeforeCancel, counter.get(), "Task should stop executing after cancel");
    }

    @Test
    void testIsCancelled() throws InterruptedException {
        var future = adapter.scheduleAtFixedRate(() -> {}, Duration.ofMillis(100));

        assertFalse(future.isCancelled());

        future.cancel(false);

        assertTrue(future.isCancelled());
    }

    @Test
    void testIsDone() throws InterruptedException {
        var future = adapter.scheduleAtFixedRate(() -> {}, Duration.ofMillis(100));

        assertFalse(future.isDone());

        future.cancel(false);

        assertTrue(future.isDone());
    }
}