package cn.structure.infra.schedule;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LocalThreadTaskSchedulerTest {

    private LocalThreadTaskScheduler scheduler;
    private DefaultTaskHandlerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DefaultTaskHandlerRegistry();
        scheduler = new LocalThreadTaskScheduler(2, registry);
    }

    @AfterEach
    void tearDown() {
        scheduler.getAllTasks().forEach(task -> scheduler.remove(task.getTaskId()));
    }

    @Test
    void testScheduleFixedRateTask() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        String handlerName = "fixed-rate-handler";
        registry.register(handlerName, param -> counter.incrementAndGet());

        ScheduleTask task = ScheduleTask.builder()
                .taskId("test-fixed-rate")
                .taskName("Fixed Rate Task")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(100L)
                .initialDelay(0L)
                .build();

        scheduler.schedule(task);

        Thread.sleep(500);

        assertTrue(counter.get() >= 4, "Fixed rate task should execute at least 4 times");
        assertEquals(ScheduleTask.TaskStatus.RUNNING, scheduler.getTaskInfo("test-fixed-rate").getStatus());

        scheduler.remove("test-fixed-rate");
        registry.unregister(handlerName);
    }

    @Test
    void testScheduleFixedDelayTask() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        String handlerName = "fixed-delay-handler";
        registry.register(handlerName, param -> counter.incrementAndGet());

        ScheduleTask task = ScheduleTask.builder()
                .taskId("test-fixed-delay")
                .taskName("Fixed Delay Task")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_DELAY)
                .delay(100L)
                .initialDelay(0L)
                .build();

        scheduler.schedule(task);

        Thread.sleep(500);

        assertTrue(counter.get() >= 4, "Fixed delay task should execute at least 4 times");

        scheduler.remove("test-fixed-delay");
        registry.unregister(handlerName);
    }

    @Test
    void testPauseAndResumeTask() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        String handlerName = "pause-resume-handler";
        registry.register(handlerName, param -> counter.incrementAndGet());

        ScheduleTask task = ScheduleTask.builder()
                .taskId("test-pause-resume")
                .taskName("Pause Resume Task")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(100L)
                .build();

        scheduler.schedule(task);

        Thread.sleep(300);
        int countBeforePause = counter.get();

        scheduler.pause("test-pause-resume");
        assertEquals(ScheduleTask.TaskStatus.PAUSED, scheduler.getTaskInfo("test-pause-resume").getStatus());

        Thread.sleep(300);
        int countAfterPause = counter.get();
        assertEquals(countBeforePause, countAfterPause, "Task should not execute while paused");

        scheduler.resume("test-pause-resume");
        assertEquals(ScheduleTask.TaskStatus.RUNNING, scheduler.getTaskInfo("test-pause-resume").getStatus());

        Thread.sleep(300);
        assertTrue(counter.get() > countAfterPause, "Task should resume executing");

        scheduler.remove("test-pause-resume");
        registry.unregister(handlerName);
    }

    @Test
    void testRemoveTask() throws InterruptedException {
        String handlerName = "remove-handler";
        registry.register(handlerName, param -> {});

        ScheduleTask task = ScheduleTask.builder()
                .taskId("test-remove")
                .taskName("Remove Task")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(100L)
                .build();

        scheduler.schedule(task);
        assertNotNull(scheduler.getTaskInfo("test-remove"));

        scheduler.remove("test-remove");

        assertNull(scheduler.getTaskInfo("test-remove"));
        registry.unregister(handlerName);
    }

    @Test
    void testGetAllTasks() throws InterruptedException {
        String handlerName1 = "handler-1";
        String handlerName2 = "handler-2";
        registry.register(handlerName1, param -> {});
        registry.register(handlerName2, param -> {});

        ScheduleTask task1 = ScheduleTask.builder()
                .taskId("task-1")
                .taskName("Task 1")
                .handlerName(handlerName1)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(1000L)
                .build();

        ScheduleTask task2 = ScheduleTask.builder()
                .taskId("task-2")
                .taskName("Task 2")
                .handlerName(handlerName2)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(1000L)
                .build();

        scheduler.schedule(task1);
        scheduler.schedule(task2);

        Thread.sleep(50);

        assertEquals(2, scheduler.getAllTasks().size());

        scheduler.remove("task-1");
        scheduler.remove("task-2");
        registry.unregister(handlerName1);
        registry.unregister(handlerName2);

        assertEquals(0, scheduler.getAllTasks().size());
    }

    @Test
    void testUpdateTask() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        String handlerName = "update-handler";
        registry.register(handlerName, param -> counter.incrementAndGet());

        ScheduleTask task = ScheduleTask.builder()
                .taskId("test-update")
                .taskName("Update Task")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(500L)
                .build();

        scheduler.schedule(task);

        Thread.sleep(1200);
        int countBeforeUpdate = counter.get();

        ScheduleTask updatedTask = ScheduleTask.builder()
                .taskId("test-update")
                .taskName("Updated Task")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(100L)
                .build();

        scheduler.update(updatedTask);

        Thread.sleep(500);

        assertTrue(counter.get() > countBeforeUpdate + 2, "Updated task should execute more frequently");

        scheduler.remove("test-update");
        registry.unregister(handlerName);
    }

    @Test
    void testScheduleWithNullTask() {
        assertThrows(IllegalArgumentException.class, () -> scheduler.schedule(null));
    }

    @Test
    void testScheduleWithNullTaskId() {
        String handlerName = "test-handler";
        registry.register(handlerName, param -> {});

        ScheduleTask task = ScheduleTask.builder()
                .taskId(null)
                .taskName("Test Task")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(1000L)
                .build();

        assertThrows(IllegalArgumentException.class, () -> scheduler.schedule(task));
        registry.unregister(handlerName);
    }

    @Test
    void testScheduleWithNullHandlerName() {
        ScheduleTask task = ScheduleTask.builder()
                .taskId("test-null-handler")
                .taskName("Test Task")
                .handlerName(null)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(1000L)
                .build();

        assertThrows(IllegalArgumentException.class, () -> scheduler.schedule(task));
    }

    @Test
    void testScheduleWithNonExistentHandler() {
        ScheduleTask task = ScheduleTask.builder()
                .taskId("test-non-existent")
                .taskName("Test Task")
                .handlerName("non-existent-handler")
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(1000L)
                .build();

        assertThrows(IllegalArgumentException.class, () -> scheduler.schedule(task));
    }

    @Test
    void testScheduleWithNullScheduleType() {
        String handlerName = "test-type-handler";
        registry.register(handlerName, param -> {});

        ScheduleTask task = ScheduleTask.builder()
                .taskId("test-null-type")
                .taskName("Test Task")
                .handlerName(handlerName)
                .scheduleType(null)
                .period(1000L)
                .build();

        assertThrows(IllegalArgumentException.class, () -> scheduler.schedule(task));
        registry.unregister(handlerName);
    }

    @Test
    void testPauseNonExistentTask() {
        assertDoesNotThrow(() -> scheduler.pause("non-existent"));
    }

    @Test
    void testResumeNonExistentTask() {
        assertDoesNotThrow(() -> scheduler.resume("non-existent"));
    }

    @Test
    void testRemoveNonExistentTask() {
        assertDoesNotThrow(() -> scheduler.remove("non-existent"));
    }

    @Test
    void testResumeNotPausedTask() throws InterruptedException {
        String handlerName = "resume-handler";
        registry.register(handlerName, param -> {});

        ScheduleTask task = ScheduleTask.builder()
                .taskId("test-resume-running")
                .taskName("Resume Running Task")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(1000L)
                .build();

        scheduler.schedule(task);

        Thread.sleep(50);

        scheduler.resume("test-resume-running");

        assertNotNull(scheduler.getTaskInfo("test-resume-running"));

        scheduler.remove("test-resume-running");
        registry.unregister(handlerName);
    }
}