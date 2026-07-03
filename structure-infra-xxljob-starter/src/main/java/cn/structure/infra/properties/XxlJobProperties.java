package cn.structure.infra.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "structure.schedule.xxl-job")
public class XxlJobProperties {

    private boolean enabled = true;

    private Integer jobGroup = 1;

}