package cn.structure.infra.configuration;

import org.springframework.context.annotation.Configuration;

/**
 * 仓储自动装配配置类
 * <p>
 * 负责注册仓储框架的核心组件。
 * <p>
 * 核心功能：
 * 1. RepositoryDelegate 通过 Spring @Autowired 自动注入到 RepositoryFacade
 * 2. 支持 CQRS 模式，通过 @ReadDelegate 限定注解区分读代理
 * 3. 当找不到匹配的 Delegate 时，由各存储模块的 AutoConfiguration 通过 @ConditionalOnMissingBean 提供默认实现
 *
 * @author chuck
 * @version 1.0.2
 * @since 2026/6/28
 */
@Configuration
public class AutoRepositoryConfiguration {

}