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

package cn.structure.infra.sample.schedule.config;

import cn.structure.infra.schedule.ScheduleTask;
import cn.structure.infra.schedule.TaskScheduler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@DependsOn("demoTaskHandlers")
public class ScheduleDemoConfig {

    @Autowired
    private TaskScheduler taskScheduler;

    @PostConstruct
    public void initScheduledTasks() {
        ScheduleTask fixedRateTask = ScheduleTask.builder()
                .taskId("demo-fixed-rate-task")
                .taskName("固定速率示例任务")
                .handlerName("demo-fixed-rate-handler")
                .handlerParam("fixed-rate-param")
                .scheduleType(ScheduleTask.ScheduleType.FIXED_RATE)
                .period(3000L)
                .initialDelay(1000L)
                .timeUnit(TimeUnit.MILLISECONDS)
                .build();
        taskScheduler.schedule(fixedRateTask);
        log.info("已启动固定速率任务: {}", fixedRateTask.getTaskName());

        ScheduleTask fixedDelayTask = ScheduleTask.builder()
                .taskId("demo-fixed-delay-task")
                .taskName("固定延迟示例任务")
                .handlerName("demo-fixed-delay-handler")
                .handlerParam("fixed-delay-param")
                .scheduleType(ScheduleTask.ScheduleType.FIXED_DELAY)
                .delay(2000L)
                .initialDelay(2000L)
                .timeUnit(TimeUnit.MILLISECONDS)
                .build();
        taskScheduler.schedule(fixedDelayTask);
        log.info("已启动固定延迟任务: {}", fixedDelayTask.getTaskName());

        log.info("所有示例调度任务已启动");
    }
}