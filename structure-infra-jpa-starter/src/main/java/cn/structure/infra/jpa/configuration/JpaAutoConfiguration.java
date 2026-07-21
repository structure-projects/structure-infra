package cn.structure.infra.jpa.configuration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
@EnableJpaRepositories
@EnableTransactionManagement
public class JpaAutoConfiguration {
}
