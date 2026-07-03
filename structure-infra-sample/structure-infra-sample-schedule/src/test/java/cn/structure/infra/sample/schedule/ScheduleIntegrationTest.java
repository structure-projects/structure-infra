package cn.structure.infra.sample.schedule;

import cn.structure.infra.schedule.ScheduleTask;
import cn.structure.infra.schedule.TaskHandlerRegistry;
import cn.structure.infra.schedule.TaskScheduler;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest(classes = ScheduleSampleApplication.class)
class ScheduleIntegrationTest {

    @Autowired
    private TaskScheduler taskScheduler;

    @Autowired
    private TaskHandlerRegistry handlerRegistry;

    @BeforeEach
    void setUp() {
        taskScheduler.getAllTasks().forEach(task -> taskScheduler.remove(task.getTaskId()));
    }

    @Test
    void testFixedRateSchedule() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        String handlerName = "test-fixed-rate-counter";
        handlerRegistry.register(handlerName, param -> {
            int count = counter.incrementAndGet();
            log.info("Fixed rate task executed, count={}", count);
        });

        String taskId = "test-fixed-rate";

        ScheduleTask task = ScheduleTask.builder()
                .taskId(taskId)
                .taskName("固定速率测试任务")
                .handlerName(handlerName)
                .handlerParam("test-param")
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(500L)
                .initialDelay(0L)
                .build();

        taskScheduler.schedule(task);

        Thread.sleep(2000);

        ScheduleTask taskInfo = taskScheduler.getTaskInfo(taskId);
        assertNotNull(taskInfo);
        assertEquals(ScheduleTask.TaskStatus.RUNNING, taskInfo.getStatus());

        assertTrue(counter.get() >= 3, "任务应至少执行3次");

        taskScheduler.pause(taskId);

        Thread.sleep(1000);

        int countAfterPause = counter.get();

        Thread.sleep(1000);

        assertEquals(countAfterPause, counter.get(), "暂停后任务不应继续执行");

        taskScheduler.resume(taskId);

        Thread.sleep(1000);

        assertTrue(counter.get() > countAfterPause, "恢复后任务应继续执行");

        taskScheduler.remove(taskId);
        handlerRegistry.unregister(handlerName);

        Thread.sleep(500);

        assertNull(taskScheduler.getTaskInfo(taskId), "删除后任务信息应为空");
    }

    @Test
    void testFixedDelaySchedule() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        String handlerName = "test-fixed-delay-counter";
        handlerRegistry.register(handlerName, param -> {
            int count = counter.incrementAndGet();
            log.info("Fixed delay task executed, count={}", count);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        String taskId = "test-fixed-delay";

        ScheduleTask task = ScheduleTask.builder()
                .taskId(taskId)
                .taskName("固定延迟测试任务")
                .handlerName(handlerName)
                .handlerParam("test-param")
                .scheduleType(ScheduleTask.ScheduleType.FIXED_DELAY)
                .delay(500L)
                .initialDelay(0L)
                .build();

        taskScheduler.schedule(task);

        Thread.sleep(2000);

        assertTrue(counter.get() >= 3, "固定延迟任务应至少执行3次");

        taskScheduler.remove(taskId);
        handlerRegistry.unregister(handlerName);
    }

    @Test
    void testUpdateTask() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        String handlerName = "test-update-handler";
        handlerRegistry.register(handlerName, param -> {
            int count = counter.incrementAndGet();
            log.info("Update test task executed, count={}", count);
        });

        String taskId = "test-update";

        ScheduleTask task = ScheduleTask.builder()
                .taskId(taskId)
                .taskName("更新测试任务")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(1000L)
                .build();

        taskScheduler.schedule(task);

        Thread.sleep(2500);

        int countBeforeUpdate = counter.get();

        ScheduleTask updatedTask = ScheduleTask.builder()
                .taskId(taskId)
                .taskName("更新测试任务-修改后")
                .handlerName(handlerName)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(500L)
                .build();

        taskScheduler.update(updatedTask);

        Thread.sleep(1500);

        assertTrue(counter.get() > countBeforeUpdate + 1, "更新后任务应更频繁执行");

        taskScheduler.remove(taskId);
        handlerRegistry.unregister(handlerName);
    }

    @Test
    void testGetAllTasks() throws InterruptedException {
        String handlerName1 = "test-get-all-handler-1";
        String handlerName2 = "test-get-all-handler-2";
        handlerRegistry.register(handlerName1, param -> log.info("Task 1 executed"));
        handlerRegistry.register(handlerName2, param -> log.info("Task 2 executed"));

        String taskId1 = "test-get-all-1";
        String taskId2 = "test-get-all-2";

        ScheduleTask task1 = ScheduleTask.builder()
                .taskId(taskId1)
                .taskName("任务1")
                .handlerName(handlerName1)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(1000L)
                .build();

        ScheduleTask task2 = ScheduleTask.builder()
                .taskId(taskId2)
                .taskName("任务2")
                .handlerName(handlerName2)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(1000L)
                .build();

        taskScheduler.schedule(task1);
        taskScheduler.schedule(task2);

        Thread.sleep(100);

        assertEquals(2, taskScheduler.getAllTasks().size());

        taskScheduler.remove(taskId1);
        taskScheduler.remove(taskId2);
        handlerRegistry.unregister(handlerName1);
        handlerRegistry.unregister(handlerName2);

        assertEquals(0, taskScheduler.getAllTasks().size());
    }

    @Test
    void testPauseNonExistentTask() {
        taskScheduler.pause("non-existent-task");
        assertNull(taskScheduler.getTaskInfo("non-existent-task"));
    }

    @Test
    void testResumeNonExistentTask() {
        taskScheduler.resume("non-existent-task");
        assertNull(taskScheduler.getTaskInfo("non-existent-task"));
    }

    @Test
    void testRemoveNonExistentTask() {
        taskScheduler.remove("non-existent-task");
        assertNull(taskScheduler.getTaskInfo("non-existent-task"));
    }

    @Test
    void testScheduleWithNullTask() {
        assertThrows(IllegalArgumentException.class, () -> taskScheduler.schedule(null));
    }

    @Test
    void testScheduleWithNullTaskId() {
        ScheduleTask task = ScheduleTask.builder()
                .taskId(null)
                .taskName("测试任务")
                .handlerName("test-handler")
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(1000L)
                .build();

        assertThrows(IllegalArgumentException.class, () -> taskScheduler.schedule(task));
    }

    @Test
    void testScheduleWithNullHandlerName() {
        ScheduleTask task = ScheduleTask.builder()
                .taskId("test-null-handler")
                .taskName("测试任务")
                .handlerName(null)
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(1000L)
                .build();

        assertThrows(IllegalArgumentException.class, () -> taskScheduler.schedule(task));
    }

    @Test
    void testScheduleWithEmptyHandlerName() {
        ScheduleTask task = ScheduleTask.builder()
                .taskId("test-empty-handler")
                .taskName("测试任务")
                .handlerName("")
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(1000L)
                .build();

        assertThrows(IllegalArgumentException.class, () -> taskScheduler.schedule(task));
    }

    @Test
    void testScheduleWithNonExistentHandler() {
        ScheduleTask task = ScheduleTask.builder()
                .taskId("test-non-existent-handler")
                .taskName("测试任务")
                .handlerName("non-existent-handler")
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(1000L)
                .build();

        assertThrows(IllegalArgumentException.class, () -> taskScheduler.schedule(task));
    }

    @Test
    void testScheduleWithNullScheduleType() {
        String handlerName = "test-null-type-handler";
        handlerRegistry.register(handlerName, param -> {});

        ScheduleTask task = ScheduleTask.builder()
                .taskId("test-null-type")
                .taskName("测试任务")
                .handlerName(handlerName)
                .scheduleType(null)
                .period(1000L)
                .build();

        assertThrows(IllegalArgumentException.class, () -> taskScheduler.schedule(task));

        handlerRegistry.unregister(handlerName);
    }

    @Test
    void testAutoConfiguration() {
        assertNotNull(taskScheduler);
        assertNotNull(handlerRegistry);
    }
}