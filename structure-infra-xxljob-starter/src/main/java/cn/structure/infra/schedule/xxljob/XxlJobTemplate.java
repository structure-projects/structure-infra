package cn.structure.infra.schedule.xxljob;

public interface XxlJobTemplate {

    String add(String jobName, String cronExpression, String handlerName, String handlerParam);

    void update(String jobId, String jobName, String cronExpression, String handlerName, String handlerParam);

    void remove(String jobId);

    void pause(String jobId);

    void start(String jobId);

    String getJobId(String handlerName);
}