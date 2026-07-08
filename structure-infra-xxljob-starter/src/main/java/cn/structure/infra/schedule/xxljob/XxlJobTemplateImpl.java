package cn.structure.infra.schedule.xxljob;

import cn.structure.infra.properties.XxlJobProperties;
import cn.structure.job.dto.XxlJobInfoDTO;
import cn.structure.job.enums.ExecutorRouteStrategyEnum;
import cn.structure.job.rpc.XxlJobClient;
import com.xxl.job.core.constant.ExecutorBlockStrategyEnum;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.tool.response.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link XxlJobTemplate} 的默认实现，基于 {@link XxlJobClient} 完成对 XXL-Job 调度中心的
 * 添加/更新/删除/暂停/启动等远程操作。
 *
 * <p><b>设计意图：</b>统一封装 XXL-Job RPC 调用的细节，包括：</p>
 * <ul>
 *     <li>构造 {@link XxlJobInfoDTO} 任务对象（默认执行器路由策略、阻塞策略、超时、重试次数等）</li>
 *     <li>解析 {@link Response} 返回值，按 {@link XxlJobContext#HANDLE_CODE_SUCCESS} 判断成败</li>
 *     <li>失败时抛出 {@link RuntimeException}，由上层调度器统一处理</li>
 * </ul>
 *
 * <p><b>协作关系：</b>由 {@code AutoXxlJobConfiguration} 装配，注入到 {@link XxlJobTaskScheduler}
 * 中作为底层 XXL-Job 操作通道。{@code XxlJobTaskScheduler} 通过本类将业务侧的 taskId 转换为
 * XXL-Job 侧的 jobId 后调用对应方法。</p>
 *
 * <p><b>默认 job 配置：</b>{@link #buildJobInfo(Integer, String, String, String, String)} 中
 * 设置了默认配置项（路由策略 FIRST、阻塞策略 SERIAL_EXECUTION、超时 300s、失败重试 1 次、
 * Glue 类型 BEAN），对应 XXL-Job 默认 job 配置表的初始值。</p>
 */
@Slf4j
@AllArgsConstructor
public class XxlJobTemplateImpl implements XxlJobTemplate {

    /**
     * XXL-Job 远程调用客户端。
     */
    private final XxlJobClient xxlJobClient;

    /**
     * XXL-Job 配置属性，主要用于获取 jobGroup。
     */
    private final XxlJobProperties jobProperties;

    /**
     * 在 XXL-Job 调度中心添加一个新任务。
     *
     * @param jobName        任务名称
     * @param cronExpression CRON 表达式（XXL-Job 使用 6 位 CRON）
     * @param handlerName    执行器侧的 handler 名称
     * @param handlerParam   handler 执行参数
     * @return 新创建的 XXL-Job 任务 ID（字符串形式）
     * @throws RuntimeException 当 XXL-Job 调用返回失败码时抛出
     */
    @Override
    public String add(String jobName, String cronExpression, String handlerName, String handlerParam) {
        // 构造 XxlJobInfoDTO（id 为 null 表示新增）
        XxlJobInfoDTO jobInfo = buildJobInfo(null, jobName, cronExpression, handlerName, handlerParam);
        Response<String> returnT = xxlJobClient.add(jobInfo);
        // 按 XXL-Job 成功码判断结果
        if (returnT.getCode() == XxlJobContext.HANDLE_CODE_SUCCESS) {
            log.info("XXL-Job add success: jobName={}, handlerName={}, jobId={}", jobName, handlerName, returnT.getData());
            return returnT.getData();
        } else {
            log.error("XXL-Job add failed: jobName={}, handlerName={}, message={}", jobName, handlerName, returnT.getMsg());
            throw new RuntimeException("XXL-Job add failed: " + returnT.getMsg());
        }
    }

    /**
     * 更新已有的 XXL-Job 任务配置。
     *
     * @param jobId          XXL-Job 任务 ID（字符串形式，内部转为 Integer）
     * @param jobName        任务名称
     * @param cronExpression CRON 表达式
     * @param handlerName    执行器侧的 handler 名称
     * @param handlerParam   handler 执行参数
     * @throws RuntimeException 当 XXL-Job 调用返回失败码时抛出
     */
    @Override
    public void update(String jobId, String jobName, String cronExpression, String handlerName, String handlerParam) {
        // jobId 字符串转 Integer，作为更新主键
        XxlJobInfoDTO jobInfo = buildJobInfo(Integer.parseInt(jobId), jobName, cronExpression, handlerName, handlerParam);
        Response<String> returnT = xxlJobClient.update(jobInfo);
        if (returnT.getCode() == XxlJobContext.HANDLE_CODE_SUCCESS) {
            log.info("XXL-Job update success: jobId={}, jobName={}, handlerName={}", jobId, jobName, handlerName);
        } else {
            log.error("XXL-Job update failed: jobId={}, jobName={}, message={}", jobId, jobName, returnT.getMsg());
            throw new RuntimeException("XXL-Job update failed: " + returnT.getMsg());
        }
    }

    /**
     * 从 XXL-Job 调度中心移除任务。
     *
     * @param jobId XXL-Job 任务 ID
     * @throws RuntimeException 当 XXL-Job 调用返回失败码时抛出
     */
    @Override
    public void remove(String jobId) {
        Response<String> returnT = xxlJobClient.remove(jobId);
        if (returnT.getCode() == XxlJobContext.HANDLE_CODE_SUCCESS) {
            log.info("XXL-Job remove success: jobId={}", jobId);
        } else {
            log.error("XXL-Job remove failed: jobId={}, message={}", jobId, returnT.getMsg());
            throw new RuntimeException("XXL-Job remove failed: " + returnT.getMsg());
        }
    }

    /**
     * 暂停 XXL-Job 任务调度。
     *
     * @param jobId XXL-Job 任务 ID
     * @throws RuntimeException 当 XXL-Job 调用返回失败码时抛出
     */
    @Override
    public void pause(String jobId) {
        Response<String> returnT = xxlJobClient.pause(jobId);
        if (returnT.getCode() == XxlJobContext.HANDLE_CODE_SUCCESS) {
            log.info("XXL-Job pause success: jobId={}", jobId);
        } else {
            log.error("XXL-Job pause failed: jobId={}, message={}", jobId, returnT.getMsg());
            throw new RuntimeException("XXL-Job pause failed: " + returnT.getMsg());
        }
    }

    /**
     * 启动（或恢复）XXL-Job 任务调度。
     *
     * @param jobId XXL-Job 任务 ID
     * @throws RuntimeException 当 XXL-Job 调用返回失败码时抛出
     */
    @Override
    public void start(String jobId) {
        Response<String> returnT = xxlJobClient.start(jobId);
        if (returnT.getCode() == XxlJobContext.HANDLE_CODE_SUCCESS) {
            log.info("XXL-Job start success: jobId={}", jobId);
        } else {
            log.error("XXL-Job start failed: jobId={}, message={}", jobId, returnT.getMsg());
            throw new RuntimeException("XXL-Job start failed: " + returnT.getMsg());
        }
    }

    /**
     * 根据 handler 名称查询 XXL-Job 任务 ID。
     *
     * <p>当前为占位实现，始终返回 {@code null}。</p>
     *
     * @param handlerName handler 名称
     * @return 始终返回 {@code null}
     */
    @Override
    public String getJobId(String handlerName) {
        return null;
    }

    /**
     * 构造 {@link XxlJobInfoDTO} 任务对象，填充默认 job 配置。
     *
     * <p>默认配置项：</p>
     * <ul>
     *     <li>jobGroup：取自 {@link XxlJobProperties#getJobGroup()}</li>
     *     <li>author：固定为 {@code "system"}</li>
     *     <li>executorRouteStrategy：{@code FIRST}（第一个执行器）</li>
     *     <li>executorBlockStrategy：{@code SERIAL_EXECUTION}（串行执行）</li>
     *     <li>executorTimeout：300 秒</li>
     *     <li>executorFailRetryCount：1 次</li>
     *     <li>glueType：{@code BEAN}（Bean 模式）</li>
     * </ul>
     *
     * @param id             任务 ID，新增时为 {@code null}
     * @param jobName        任务名称
     * @param cronExpression CRON 表达式
     * @param handlerName    handler 名称
     * @param handlerParam   handler 参数
     * @return 填充完成的 XxlJobInfoDTO
     */
    private XxlJobInfoDTO buildJobInfo(Integer id, String jobName, String cronExpression, String handlerName, String handlerParam) {
        XxlJobInfoDTO jobInfo = new XxlJobInfoDTO();
        jobInfo.setId(id);
        jobInfo.setJobGroup(jobProperties.getJobGroup());
        jobInfo.setJobCron(cronExpression);
        jobInfo.setJobDesc(jobName);
        jobInfo.setAuthor("system");
        jobInfo.setExecutorHandler(handlerName);
        jobInfo.setExecutorParam(handlerParam);
        jobInfo.setExecutorRouteStrategy(ExecutorRouteStrategyEnum.FIRST.name());
        jobInfo.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.name());
        jobInfo.setExecutorTimeout(300);
        jobInfo.setExecutorFailRetryCount(1);
        jobInfo.setGlueType(GlueTypeEnum.BEAN.name());
        return jobInfo;
    }
}