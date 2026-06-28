package cn.structure.infra.jpa.configuration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA 自动配置类
 * <p>
 * 当检测到 JPA 相关依赖时自动配置
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
@ConditionalOnProperty(prefix = "structure.infra", name = "type", havingValue = "JPA", matchIfMissing = true)
@EnableJpaRepositories
@EnableTransactionManagement
public class JpaAutoConfiguration {

}
