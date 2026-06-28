package cn.structure.infra.sample.cqrs;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CQRS 示例启动类
 * <p>
 * 演示读写分离模式：
 * - 写操作通过 MyBatis Plus 代理执行
 * - 读操作通过 Elasticsearch 代理执行
 * <p>
 * 同时加载多种目标代理：
 * - BASE 代理（MyBatis Plus）：负责写操作
 * - READ 代理（Elasticsearch）：负责读操作
 * <p>
 * 当读代理执行失败时，自动回退到 BASE 代理执行读操作。
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@SpringBootApplication(scanBasePackages = {
        "cn.structure.infra.sample",
        "cn.structure.infra.repository",
        "cn.structure.infra.mybatis.plus",
        "cn.structure.infra.elasticsearch"
})
@MapperScan("cn.structure.infra.sample.infra.mapper")
public class CqrsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CqrsApplication.class, args);
    }
}