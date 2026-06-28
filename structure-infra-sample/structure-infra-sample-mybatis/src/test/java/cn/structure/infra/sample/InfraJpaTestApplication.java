package cn.structure.infra.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * JPA 测试启动类
 * <p>
 * 用于 JPA 场景测试，排除 MyBatis Plus 自动配置
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@SpringBootApplication(scanBasePackages = "cn.structure.infra.sample")
public class InfraJpaTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfraJpaTestApplication.class, args);
    }
}