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

package cn.structure.infra.schedule;

import java.util.List;

/**
 * 任务调度器 SPI 接口。
 *
 * <p>该接口是 schedule-starter 模块对外暴露的核心扩展点（Service Provider Interface），
 * 用于屏蔽不同调度实现（本地线程池调度、XXL-Job 分布式调度等）之间的差异。
 * 业务方或自动配置类通过此接口完成任务的注册、更新、删除、暂停、恢复等生命周期管理，
 * 而无需关心底层调度引擎的具体细节。</p>
 *
 * <p>已知实现：</p>
 * <ul>
 *     <li>{@link LocalThreadTaskScheduler}：基于 {@link java.util.concurrent.ScheduledExecutorService}
 *         的本地默认实现，适用于单机场景</li>
 *     <li>{@code XxlJobTaskScheduler}（位于 xxljob-starter 模块）：将本地任务转发到 XXL-Job
 *         的分布式实现，适用于集群/分布式场景，启用后会通过
 *         {@code @AutoConfigureBefore} 机制覆盖本地实现</li>
 * </ul>
 *
 * <p><b>设计意图：</b>采用 SPI 模式解耦调度 API 与调度实现，使得切换调度引擎时
 * 业务代码无需改动，仅通过 Spring Bean 装配即可完成切换。</p>
 */
public interface TaskScheduler {

    /**
     * 调度一个任务。
     *
     * <p>若 taskId 已存在，则先移除旧任务再创建新任务，实现"幂等调度"。
     * 调用此方法后任务将进入 {@code RUNNING} 状态。</p>
     *
     * @param task 任务描述对象，包含 taskId、handlerName、调度类型及调度参数等
     * @throws IllegalArgumentException 当 task、taskId、handlerName 为空，
     *                                  handler 未注册或 scheduleType 为空时抛出
     */
    void schedule(ScheduleTask task);

    /**
     * 更新已有任务的调度配置。
     *
     * <p>更新操作通常等价于"先移除再重新调度"。若 taskId 不存在，则仅记录日志不做任何操作。</p>
     *
     * @param task 新的任务描述对象，taskId 必须与已存在任务一致
     * @throws IllegalArgumentException 当 task 字段校验失败时抛出
     */
    void update(ScheduleTask task);

    /**
     * 移除任务并停止其调度。
     *
     * <p>任务被移除后状态置为 {@code STOPPED}，相关调度资源被释放。</p>
     *
     * @param taskId 任务唯一标识
     */
    void remove(String taskId);

    /**
     * 暂停任务调度。
     *
     * <p>暂停后任务仍保留在调度器内部记录中，状态置为 {@code PAUSED}，
     * 可通过 {@link #resume(String)} 恢复。</p>
     *
     * @param taskId 任务唯一标识
     */
    void pause(String taskId);

    /**
     * 恢复被暂停的任务调度。
     *
     * <p>仅当任务当前状态为 {@code PAUSED} 时才会真正恢复，否则忽略。</p>
     *
     * @param taskId 任务唯一标识
     */
    void resume(String taskId);

    /**
     * 根据任务 ID 查询任务信息。
     *
     * @param taskId 任务唯一标识
     * @return 任务描述对象；若任务不存在则返回 {@code null}
     */
    ScheduleTask getTaskInfo(String taskId);

    /**
     * 获取当前调度器中所有已注册任务的快照列表。
     *
     * <p>返回的是不可变副本，调用方修改不会影响调度器内部状态。</p>
     *
     * @return 任务列表的不可变副本；若无任何任务则返回空列表
     */
    List<ScheduleTask> getAllTasks();
}