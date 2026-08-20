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

package cn.structure.infra.schedule.xxljob;

/**
 * XXL-Job 操作模板接口，封装对 XXL-Job 调度中心 API 的增删改查操作。
 *
 * <p><b>设计意图：</b>将 {@code XxlJobTaskScheduler} 与 XXL-Job 客户端
 * （{@code XxlJobClient}）解耦。调度器仅依赖本接口即可完成任务的远程管理，
 * 便于替换为不同实现（如 mock 测试、自定义 RPC 等），也便于对 XXL-Job 的
 * 调用进行统一封装和监控。</p>
 *
 * <p>已知实现：{@link XxlJobTemplateImpl}（基于 {@code XxlJobClient} 的默认实现）。</p>
 *
 * <p><b>与 taskId↔xxlJobId 映射的关系：</b>本接口方法以 {@code jobId}（XXL-Job 侧 ID）
 * 为操作单元，而 {@code XxlJobTaskScheduler} 在外部以 {@code taskId}（业务侧 ID）为操作单元。
 * 两者之间的映射由 {@code XxlJobTaskScheduler} 内部的 Map 维护，本接口不感知 taskId。</p>
 */
public interface XxlJobTemplate {

    /**
     * 在 XXL-Job 调度中心添加一个新任务。
     *
     * @param jobName        任务名称（描述性信息）
     * @param cronExpression CRON 表达式（XXL-Job 使用 6 位 CRON，无年字段）
     * @param handlerName    执行器侧的 handler 名称
     * @param handlerParam   handler 执行参数
     * @return 新创建的 XXL-Job 任务 ID（字符串形式）
     * @throws RuntimeException 当 XXL-Job 调用失败时抛出
     */
    String add(String jobName, String cronExpression, String handlerName, String handlerParam);

    /**
     * 更新已有的 XXL-Job 任务配置。
     *
     * @param jobId          XXL-Job 任务 ID
     * @param jobName        任务名称
     * @param cronExpression CRON 表达式
     * @param handlerName    执行器侧的 handler 名称
     * @param handlerParam   handler 执行参数
     * @throws RuntimeException 当 XXL-Job 调用失败时抛出
     */
    void update(String jobId, String jobName, String cronExpression, String handlerName, String handlerParam);

    /**
     * 从 XXL-Job 调度中心移除任务。
     *
     * @param jobId XXL-Job 任务 ID
     * @throws RuntimeException 当 XXL-Job 调用失败时抛出
     */
    void remove(String jobId);

    /**
     * 暂停 XXL-Job 任务调度。
     *
     * @param jobId XXL-Job 任务 ID
     * @throws RuntimeException 当 XXL-Job 调用失败时抛出
     */
    void pause(String jobId);

    /**
     * 启动（或恢复）XXL-Job 任务调度。
     *
     * @param jobId XXL-Job 任务 ID
     * @throws RuntimeException 当 XXL-Job 调用失败时抛出
     */
    void start(String jobId);

    /**
     * 根据 handler 名称查询 XXL-Job 任务 ID。
     *
     * <p>当前实现返回 {@code null}（占位方法），可由子类按需实现。</p>
     *
     * @param handlerName handler 名称
     * @return XXL-Job 任务 ID；若未找到返回 {@code null}
     */
    String getJobId(String handlerName);
}