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

package cn.structure.infra.sample.xxljob.controller;

import cn.structure.infra.schedule.ScheduleTask;
import cn.structure.infra.schedule.TaskScheduler;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/job")
public class JobManagerController {

    @Resource
    private TaskScheduler taskScheduler;

    @PostMapping("/add")
    public ScheduleTask add(
            @RequestParam("taskId") String taskId,
            @RequestParam("taskName") String taskName,
            @RequestParam("handlerName") String handlerName,
            @RequestParam(value = "handlerParam", required = false) String handlerParam,
            @RequestParam(value = "cronExpression", defaultValue = "0/5 * * * * ?") String cronExpression) {

        ScheduleTask task = ScheduleTask.builder()
                .taskId(taskId)
                .taskName(taskName)
                .handlerName(handlerName)
                .handlerParam(handlerParam)
                .scheduleType(ScheduleTask.ScheduleType.CRON)
                .cronExpression(cronExpression)
                .build();

        taskScheduler.schedule(task);
        return taskScheduler.getTaskInfo(taskId);
    }

    @PutMapping("/update/{taskId}")
    public ScheduleTask update(
            @PathVariable("taskId") String taskId,
            @RequestParam(value = "taskName", required = false) String taskName,
            @RequestParam(value = "handlerName", required = false) String handlerName,
            @RequestParam(value = "handlerParam", required = false) String handlerParam,
            @RequestParam(value = "cronExpression", required = false) String cronExpression) {

        ScheduleTask existingTask = taskScheduler.getTaskInfo(taskId);
        if (existingTask == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }

        ScheduleTask.ScheduleTaskBuilder builder = ScheduleTask.builder()
                .taskId(taskId)
                .taskName(taskName != null ? taskName : existingTask.getTaskName())
                .handlerName(handlerName != null ? handlerName : existingTask.getHandlerName())
                .handlerParam(handlerParam != null ? handlerParam : existingTask.getHandlerParam())
                .scheduleType(existingTask.getScheduleType());

        if (cronExpression != null) {
            builder.cronExpression(cronExpression);
        } else if (existingTask.getCronExpression() != null) {
            builder.cronExpression(existingTask.getCronExpression());
        }

        ScheduleTask task = builder.build();
        taskScheduler.update(task);
        return taskScheduler.getTaskInfo(taskId);
    }

    @DeleteMapping("/remove/{taskId}")
    public String remove(@PathVariable("taskId") String taskId) {
        ScheduleTask task = taskScheduler.getTaskInfo(taskId);
        if (task == null) {
            return "Task not found: " + taskId;
        }
        taskScheduler.remove(taskId);
        return "Removed task: " + taskId;
    }

    @PutMapping("/pause/{taskId}")
    public ScheduleTask pause(@PathVariable("taskId") String taskId) {
        taskScheduler.pause(taskId);
        return taskScheduler.getTaskInfo(taskId);
    }

    @PutMapping("/resume/{taskId}")
    public ScheduleTask resume(@PathVariable("taskId") String taskId) {
        taskScheduler.resume(taskId);
        return taskScheduler.getTaskInfo(taskId);
    }

    @GetMapping("/info/{taskId}")
    public ScheduleTask getTaskInfo(@PathVariable("taskId") String taskId) {
        return taskScheduler.getTaskInfo(taskId);
    }

    @GetMapping("/list")
    public List<ScheduleTask> getAllTasks() {
        return taskScheduler.getAllTasks();
    }
}