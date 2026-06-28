package cn.structure.infra.configuration;

import cn.structure.infra.repository.RepositoryBeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 仓储自动装配配置类
 * <p>
 * 负责注册仓储框架的核心组件，实现 RepositoryDelegate 到 RepositoryFacade 的自动注入机制。
 * <p>
 * 核心功能：
 * 1. 注册 {@link RepositoryBeanPostProcessor}，在 Spring 容器初始化过程中扫描所有 Delegate 和 Facade
 * 2. 自动将匹配的 RepositoryDelegate 注入到对应的 RepositoryFacade 中
 * 3. 支持 CQRS 模式，区分 BASE（写）和 READ（读）两种 Delegate
 * 4. 当找不到匹配的 Delegate 时，自动尝试通过 DelegateFactory 创建，或使用默认的内存实现
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Configuration
public class AutoRepositoryConfiguration {

    /**
     * 注册 Repository Bean 后处理器
     * <p>
     * 这是仓储框架的核心组件，负责在 Spring 容器启动过程中完成以下工作：
     * <p>
     * 1. **收集阶段（postProcessBeforeInitialization）**：
     *    - 扫描所有带有 {@link cn.structure.infra.annotations.DelegateFor} 注解的 Bean
     *    - 收集 RepositoryDelegate 和 IQueryDelegate 的元信息
     *    - 按 priority 优先级排序
     * <p>
     * 2. **识别阶段（postProcessAfterInitialization）**：
     *    - 识别所有 RepositoryFacade 的实例
     *    - 记录 Facade 的 Bean 名称和引用
     * <p>
     * 3. **注入阶段（ContextRefreshedEvent）**：
     *    - 根据泛型参数和注解配置，为每个 Facade 查找匹配的 Delegate
     *    - 优先使用用户自定义的 Delegate（通过 @DelegateFor 声明）
     *    - 若无匹配，自动通过 {@link cn.structure.infra.repository.RepositoryDelegateFactory} 创建
     *    - 最后回退到默认的 {@link cn.structure.infra.repository.InMemoryRepositoryDelegate}
     *    - 支持 CQRS 模式，分别注入 BASE 和 READ Delegate
     *
     * @return RepositoryBeanPostProcessor 实例
     */
    @Bean
    public static RepositoryBeanPostProcessor repositoryBeanPostProcessor() {
        return new RepositoryBeanPostProcessor();
    }
}
