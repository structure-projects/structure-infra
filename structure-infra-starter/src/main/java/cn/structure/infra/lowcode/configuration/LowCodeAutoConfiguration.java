package cn.structure.infra.lowcode.configuration;

import cn.structure.infra.lowcode.properties.LowCodeProperties;
import cn.structure.infra.lowcode.registry.ResourceSchemaBuilder;
import cn.structure.infra.lowcode.repository.LowCodeRepoFactory;
import cn.structure.infra.lowcode.router.LowCodeRepositoryRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 低代码仓储自动配置类
 * <p>
 * 负责低代码仓储体系的自动装配，核心功能：
 * <ul>
 *   <li>注册 {@link LowCodeRepositoryRouter} 作为低代码仓储的统一入口</li>
 *   <li>加载 YAML 配置中的资源定义，自动注册到路由引擎</li>
 *   <li>收集所有 {@link LowCodeRepoFactory} 实现，供路由引擎使用</li>
 * </ul>
 * <p>
 * 可通过 {@code structure.infra.lowcode.enabled=false} 关闭低代码功能。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(LowCodeProperties.class)
@ConditionalOnProperty(prefix = "structure.infra.lowcode", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LowCodeAutoConfiguration {

    /**
     * 注册低代码仓储路由引擎
     * <p>
     * 路由引擎是低代码仓储体系的核心调度器，负责：
     * <ol>
     *   <li>接收所有 LowCodeRepoFactory 实现，建立存储类型到工厂的映射</li>
     *   <li>从配置中加载资源定义，自动创建对应的存储实例</li>
     *   <li>对外提供统一的 LowCodeRepository 接口</li>
     * </ol>
     *
     * @param factories  所有可用的仓储工厂
     * @param properties 低代码配置属性
     * @return 低代码仓储路由引擎实例
     */
    @Bean
    public LowCodeRepositoryRouter lowCodeRepositoryRouter(List<LowCodeRepoFactory> factories,
                                                           LowCodeProperties properties) {
        // 1. 构造路由引擎，注入所有仓储工厂
        LowCodeRepositoryRouter router = new LowCodeRepositoryRouter(factories);

        // 2. 遍历配置中的资源定义，逐个注册到路由引擎
        if (properties.getResources() != null && !properties.getResources().isEmpty()) {
            for (var entry : properties.getResources().entrySet()) {
                String resourceName = entry.getKey();
                LowCodeProperties.ResourceProperties resourceProps = entry.getValue();

                // 跳过缺少 schema 或 repository 配置的资源
                if (resourceProps.getSchema() == null || resourceProps.getRepository() == null) {
                    log.warn("Resource {} has no schema or repository config, skipped", resourceName);
                    continue;
                }

                // 3. 将配置属性转换为内部模型（ResourceSchema + RepositoryConfig）
                var schema = ResourceSchemaBuilder.buildSchema(resourceName, resourceProps.getSchema());
                var repoConfig = ResourceSchemaBuilder.buildRepositoryConfig(resourceProps.getRepository());

                // 4. 注册资源（触发存储实例创建和容器初始化）
                router.registerResource(resourceName, schema, repoConfig);
                log.info("LowCode resource registered: {}", resourceName);
            }
        }

        return router;
    }
}
