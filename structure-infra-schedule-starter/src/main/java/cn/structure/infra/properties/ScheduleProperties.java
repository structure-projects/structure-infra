package cn.structure.infra.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "structure.schedule")
public class ScheduleProperties {

    private Integer poolSize = Runtime.getRuntime().availableProcessors();
}