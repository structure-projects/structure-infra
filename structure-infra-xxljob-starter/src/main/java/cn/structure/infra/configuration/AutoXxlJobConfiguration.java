package cn.structure.infra.configuration;

import cn.structure.infra.properties.XxlJobProperties;
import cn.structure.infra.schedule.TaskScheduler;
import cn.structure.infra.schedule.xxljob.XxlJobTaskScheduler;
import cn.structure.infra.schedule.xxljob.XxlJobTemplate;
import cn.structure.infra.schedule.xxljob.XxlJobTemplateImpl;
import cn.structure.job.rpc.XxlJobClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(XxlJobProperties.class)
@AutoConfigureBefore(cn.structure.infra.configuration.AutoScheduleConfiguration.class)
public class AutoXxlJobConfiguration {

    @Bean
    @ConditionalOnMissingBean(XxlJobTemplate.class)
    public XxlJobTemplate xxlJobTemplate(XxlJobClient xxlJobClient, XxlJobProperties xxlJobProperties) {
        log.info(">>>>>>>>>>> xxl-job template init.");
        return new XxlJobTemplateImpl(xxlJobClient, xxlJobProperties);
    }

    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    public TaskScheduler taskScheduler(XxlJobTemplate xxlJobTemplate) {
        return new XxlJobTaskScheduler(xxlJobTemplate);
    }
}