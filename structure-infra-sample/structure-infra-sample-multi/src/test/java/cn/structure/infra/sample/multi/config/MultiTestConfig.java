package cn.structure.infra.sample.multi.config;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@SpringBootApplication(scanBasePackages = "cn.structure.infra.sample.multi")
@Import(MockMongoConfiguration.class)
public class MultiTestConfig {
}