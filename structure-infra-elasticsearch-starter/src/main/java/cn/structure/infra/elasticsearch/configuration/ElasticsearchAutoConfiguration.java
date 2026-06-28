package cn.structure.infra.elasticsearch.configuration;

import cn.structure.infra.elasticsearch.repository.ElasticsearchDelegateBeanPostProcessor;
import cn.structure.infra.elasticsearch.repository.ElasticsearchDelegateFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch 自动配置类
 * <p>
 * 当检测到 Elasticsearch 相关依赖（{@link org.springframework.data.elasticsearch.core.ElasticsearchOperations}）时自动配置，
 * 注册 Elasticsearch 文档操作所需的核心组件，使其与仓储框架无缝集成。
 * <p>
 * 注册的 Bean：
 * <ul>
 *   <li>{@link cn.structure.infra.elasticsearch.repository.ElasticsearchDelegateFactory} - 仓储委托工厂，
 *       负责根据 PO 类创建 {@link cn.structure.infra.elasticsearch.repository.ElasticsearchRepositoryDelegate} 实例，依赖 {@link org.springframework.data.elasticsearch.core.ElasticsearchOperations}</li>
 *   <li>{@link cn.structure.infra.elasticsearch.repository.ElasticsearchDelegateBeanPostProcessor} - Bean 后处理器，
 *       为自定义的 ElasticsearchRepositoryDelegate 实现类自动注入 ElasticsearchOperations 和实体类</li>
 * </ul>
 * <p>
 * 工作机制：
 * <ol>
 *   <li>当 {@link cn.structure.infra.repository.RepositoryFacade} 需要获取 RepositoryDelegate 时，
 *       会通过 {@link cn.structure.infra.repository.RepositoryBeanPostProcessor} 查找匹配的 Delegate</li>
 *   <li>若未找到用户自定义的 Delegate，会通过 ElasticsearchDelegateFactory 自动创建</li>
 *   <li>DelegateBeanPostProcessor 确保用户自定义的 Delegate 实现能正确注入 ElasticsearchOperations</li>
 * </ol>
 * <p>
 * 配置方式：
 * <ul>
 *   <li>默认自动启用（matchIfMissing = true）</li>
 *   <li>可通过 `structure.infra.type=ELASTICSEARCH` 显式指定</li>
 * </ul>
 * <p>
 * 额外配置：
 * <ul>
 *   <li>{@link EnableElasticsearchRepositories} - 启用 Spring Data Elasticsearch 仓库扫描</li>
 * </ul>
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.data.elasticsearch.core.ElasticsearchOperations")
@ConditionalOnProperty(prefix = "structure.infra", name = "type", havingValue = "ELASTICSEARCH", matchIfMissing = true)
@EnableElasticsearchRepositories
public class ElasticsearchAutoConfiguration {

    /**
     * 创建 Elasticsearch 仓储委托工厂
     * <p>
     * 负责根据 PO 类创建 ElasticsearchRepositoryDelegate 实例，通过 ElasticsearchOperations 进行文档操作。
     * 当 RepositoryFacade 需要获取 Elasticsearch 类型的 RepositoryDelegate 时，会通过此工厂进行创建。
     *
     * @param elasticsearchOperations Elasticsearch 操作模板，用于执行索引、查询等操作
     * @return ElasticsearchDelegateFactory 实例
     */
    @Bean
    @ConditionalOnBean(ElasticsearchOperations.class)
    public ElasticsearchDelegateFactory elasticsearchDelegateFactory(ElasticsearchOperations elasticsearchOperations) {
        return new ElasticsearchDelegateFactory(elasticsearchOperations);
    }

    /**
     * 创建 Elasticsearch 委托 Bean 后处理器
     * <p>
     * 在 Bean 初始化完成后，自动为 ElasticsearchRepositoryDelegate 实现类注入 ElasticsearchOperations 和实体类。
     * <p>
     * 处理逻辑：
     * 1. 扫描所有 Bean，筛选出 ElasticsearchRepositoryDelegate 的实例
     * 2. 从 Spring 上下文获取 ElasticsearchOperations 并注入到 Delegate 实例中
     * 3. 检查是否存在 {@link cn.structure.infra.annotations.DelegateFor} 注解
     * 4. 将注解中指定的 PO 类设置到 Delegate 实例中
     *
     * @return ElasticsearchDelegateBeanPostProcessor 实例
     */
    @Bean
    @ConditionalOnBean(ElasticsearchOperations.class)
    public ElasticsearchDelegateBeanPostProcessor elasticsearchDelegateBeanPostProcessor() {
        return new ElasticsearchDelegateBeanPostProcessor();
    }
}
