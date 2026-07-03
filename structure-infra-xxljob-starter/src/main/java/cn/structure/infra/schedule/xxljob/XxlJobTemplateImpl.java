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

@Slf4j
@AllArgsConstructor
public class XxlJobTemplateImpl implements XxlJobTemplate {

    private final XxlJobClient xxlJobClient;

    private final XxlJobProperties jobProperties;

    @Override
    public String add(String jobName, String cronExpression, String handlerName, String handlerParam) {
        XxlJobInfoDTO jobInfo = buildJobInfo(null, jobName, cronExpression, handlerName, handlerParam);
        Response<String> returnT = xxlJobClient.add(jobInfo);
        if (returnT.getCode() == XxlJobContext.HANDLE_CODE_SUCCESS) {
            log.info("XXL-Job add success: jobName={}, handlerName={}, jobId={}", jobName, handlerName, returnT.getData());
            return returnT.getData();
        } else {
            log.error("XXL-Job add failed: jobName={}, handlerName={}, message={}", jobName, handlerName, returnT.getMsg());
            throw new RuntimeException("XXL-Job add failed: " + returnT.getMsg());
        }
    }

    @Override
    public void update(String jobId, String jobName, String cronExpression, String handlerName, String handlerParam) {
        XxlJobInfoDTO jobInfo = buildJobInfo(Integer.parseInt(jobId), jobName, cronExpression, handlerName, handlerParam);
        Response<String> returnT = xxlJobClient.update(jobInfo);
        if (returnT.getCode() == XxlJobContext.HANDLE_CODE_SUCCESS) {
            log.info("XXL-Job update success: jobId={}, jobName={}, handlerName={}", jobId, jobName, handlerName);
        } else {
            log.error("XXL-Job update failed: jobId={}, jobName={}, message={}", jobId, jobName, returnT.getMsg());
            throw new RuntimeException("XXL-Job update failed: " + returnT.getMsg());
        }
    }

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

    @Override
    public String getJobId(String handlerName) {
        return null;
    }

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