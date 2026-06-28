package cn.structure.infra.configuration;

import cn.structure.infra.repository.RepositoryBeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AutoRepositoryConfiguration {

    /**
     * 注册 Repository Bean 后处理器
     * <p>
     * 自动为 RepositoryFacade 的子类注入 RepositoryDelegate
     *
     * @return RepositoryBeanPostProcessor
     */
    @Bean
    public static RepositoryBeanPostProcessor repositoryBeanPostProcessor() {
        return new RepositoryBeanPostProcessor();
    }
}
