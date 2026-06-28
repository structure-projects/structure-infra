package cn.structure.infra.mybatis.plus.configuration;

import cn.structure.infra.mybatis.plus.repository.MybatisPlusDelegateBeanPostProcessor;
import cn.structure.infra.mybatis.plus.repository.MybatisPlusDelegateFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationContext;

/**
 * MyBatis Plus 自动配置类
 * <p>
 * 当检测到 MyBatis Plus 相关依赖（{@link com.baomidou.mybatisplus.core.mapper.BaseMapper}）时自动配置，
 * 注册 MyBatis Plus 持久化所需的核心组件，使其与仓储框架无缝集成。
 * <p>
 * 注册的 Bean：
 * <ul>
 *   <li>{@link cn.structure.infra.mybatis.plus.repository.MybatisPlusDelegateFactory} - 仓储委托工厂，
 *       负责根据 PO 类自动查找对应的 BaseMapper 并创建 {@link cn.structure.infra.mybatis.plus.repository.MybatisPlusRepositoryDelegate} 实例</li>
 *   <li>{@link cn.structure.infra.mybatis.plus.repository.MybatisPlusDelegateBeanPostProcessor} - Bean 后处理器，
 *       为自定义的 MybatisPlusRepositoryDelegate 实现类自动注入 BaseMapper</li>
 * </ul>
 * <p>
 * 工作机制：
 * <ol>
 *   <li>当 {@link cn.structure.infra.repository.RepositoryFacade} 需要获取 RepositoryDelegate 时，
 *       会通过 {@link cn.structure.infra.repository.RepositoryBeanPostProcessor} 查找匹配的 Delegate</li>
 *   <li>若未找到用户自定义的 Delegate，会通过 MybatisPlusDelegateFactory 自动创建</li>
 *   <li>DelegateBeanPostProcessor 确保用户自定义的 Delegate 实现能正确注入 BaseMapper</li>
 * </ol>
 * <p>
 * 配置方式：
 * <ul>
 *   <li>默认自动启用（matchIfMissing = true）</li>
 *   <li>可通过 `structure.infra.type=MYBATIS_PLUS` 显式指定</li>
 * </ul>
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
@ConditionalOnProperty(prefix = "structure.infra", name = "type", havingValue = "MYBATIS_PLUS", matchIfMissing = true)
public class MybatisPlusAutoConfiguration {

    /**
     * 创建 MyBatis Plus 仓储委托工厂
     * <p>
     * 负责根据 PO 类自动查找对应的 BaseMapper，并创建 MybatisPlusRepositoryDelegate 实例。
     * 当 RepositoryFacade 需要获取特定类型的 RepositoryDelegate 时，会通过此工厂进行创建。
     *
     * @param applicationContext Spring 应用上下文，用于查找 Mapper Bean
     * @return MybatisPlusDelegateFactory 实例
     */
    @Bean
    public MybatisPlusDelegateFactory mybatisPlusDelegateFactory(ApplicationContext applicationContext) {
        return new MybatisPlusDelegateFactory(applicationContext);
    }

    /**
     * 创建 MyBatis Plus 委托 Bean 后处理器
     * <p>
     * 在 Bean 初始化完成后，自动为带有 {@link cn.structure.infra.annotations.DelegateFor} 注解的
     * MybatisPlusRepositoryDelegate 实现类注入对应的 BaseMapper。
     * <p>
     * 处理逻辑：
     * 1. 扫描所有 Bean，筛选出 MybatisPlusRepositoryDelegate 的实例
     * 2. 检查是否存在 {@link cn.structure.infra.annotations.DelegateFor} 注解
     * 3. 根据注解中指定的 PO 类查找对应的 BaseMapper
     * 4. 将找到的 BaseMapper 注入到 Delegate 实例中
     *
     * @return MybatisPlusDelegateBeanPostProcessor 实例
     */
    @Bean
    public MybatisPlusDelegateBeanPostProcessor mybatisPlusDelegateBeanPostProcessor() {
        return new MybatisPlusDelegateBeanPostProcessor();
    }
}
