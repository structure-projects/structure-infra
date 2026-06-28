package cn.structure.infra.jpa.configuration;

import cn.structure.infra.jpa.repository.JpaDelegateBeanPostProcessor;
import cn.structure.infra.jpa.repository.JpaDelegateFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManager;

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
@EnableJpaRepositories
@EnableTransactionManagement
public class JpaAutoConfiguration {

    @Bean
    @ConditionalOnBean(EntityManager.class)
    public JpaDelegateFactory jpaDelegateFactory(EntityManager entityManager) {
        return new JpaDelegateFactory(entityManager);
    }

    @Bean
    @ConditionalOnClass(name = "jakarta.persistence.EntityManager")
    public JpaDelegateBeanPostProcessor jpaDelegateBeanPostProcessor() {
        return new JpaDelegateBeanPostProcessor();
    }
}
