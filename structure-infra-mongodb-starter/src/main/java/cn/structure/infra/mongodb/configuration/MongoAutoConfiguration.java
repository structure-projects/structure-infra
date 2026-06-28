package cn.structure.infra.mongodb.configuration;

import cn.structure.infra.mongodb.repository.MongoDelegateBeanPostProcessor;
import cn.structure.infra.mongodb.repository.MongoDelegateFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB 自动配置类
 * <p>
 * 当检测到 MongoDB 相关依赖（{@link org.springframework.data.mongodb.core.MongoTemplate}）时自动配置，
 * 注册 MongoDB 文档操作所需的核心组件，使其与仓储框架无缝集成。
 * <p>
 * 注册的 Bean：
 * <ul>
 *   <li>{@link cn.structure.infra.mongodb.repository.MongoDelegateFactory} - 仓储委托工厂，
 *       负责根据 PO 类创建 {@link cn.structure.infra.mongodb.repository.MongoRepositoryDelegate} 实例，依赖 {@link org.springframework.data.mongodb.core.MongoTemplate}</li>
 *   <li>{@link cn.structure.infra.mongodb.repository.MongoDelegateBeanPostProcessor} - Bean 后处理器，
 *       为自定义的 MongoRepositoryDelegate 实现类自动注入 MongoTemplate 和实体类</li>
 * </ul>
 * <p>
 * 工作机制：
 * <ol>
 *   <li>当 {@link cn.structure.infra.repository.RepositoryFacade} 需要获取 RepositoryDelegate 时，
 *       会通过 {@link cn.structure.infra.repository.RepositoryBeanPostProcessor} 查找匹配的 Delegate</li>
 *   <li>若未找到用户自定义的 Delegate，会通过 MongoDelegateFactory 自动创建</li>
 *   <li>DelegateBeanPostProcessor 确保用户自定义的 Delegate 实现能正确注入 MongoTemplate</li>
 * </ol>
 * <p>
 * 配置方式：
 * <ul>
 *   <li>默认自动启用（matchIfMissing = true）</li>
 *   <li>可通过 `structure.infra.type=MONGODB` 显式指定</li>
 * </ul>
 * <p>
 * 额外配置：
 * <ul>
 *   <li>{@link EnableMongoRepositories} - 启用 Spring Data MongoDB 仓库扫描</li>
 * </ul>
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.data.mongodb.core.MongoTemplate")
@ConditionalOnProperty(prefix = "structure.infra", name = "type", havingValue = "MONGODB", matchIfMissing = true)
@EnableMongoRepositories
public class MongoAutoConfiguration {

    /**
     * 创建 MongoDB 仓储委托工厂
     * <p>
     * 负责根据 PO 类创建 MongoRepositoryDelegate 实例，通过 MongoTemplate 进行文档操作。
     * 当 RepositoryFacade 需要获取 MongoDB 类型的 RepositoryDelegate 时，会通过此工厂进行创建。
     *
     * @param mongoTemplate MongoDB 操作模板，用于执行增删改查等操作
     * @return MongoDelegateFactory 实例
     */
    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    public MongoDelegateFactory mongoDelegateFactory(MongoTemplate mongoTemplate) {
        return new MongoDelegateFactory(mongoTemplate);
    }

    /**
     * 创建 MongoDB 委托 Bean 后处理器
     * <p>
     * 在 Bean 初始化完成后，自动为 MongoRepositoryDelegate 实现类注入 MongoTemplate 和实体类。
     * <p>
     * 处理逻辑：
     * 1. 扫描所有 Bean，筛选出 MongoRepositoryDelegate 的实例
     * 2. 从 Spring 上下文获取 MongoTemplate 并注入到 Delegate 实例中
     * 3. 检查是否存在 {@link cn.structure.infra.annotations.DelegateFor} 注解
     * 4. 将注解中指定的 PO 类设置到 Delegate 实例中
     *
     * @return MongoDelegateBeanPostProcessor 实例
     */
    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    public MongoDelegateBeanPostProcessor mongoDelegateBeanPostProcessor() {
        return new MongoDelegateBeanPostProcessor();
    }
}
