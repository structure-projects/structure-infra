package cn.structure.infra.sample.infra.repository;

import org.springframework.stereotype.Component;

/**
 * 用户仓储 Elasticsearch 实现
 * <p>
 * 继承基类 {@link AbstractUserRepositoryImpl}，使用 Elasticsearch 作为持久化技术。
 * <p>
 * 注意：通过命名约定和泛型参数自动匹配对应的 Delegate 实现，
 * 无需额外的 @Repository 注解配置。
 *
 * @author chuck
 * @version 1.0.2
 * @since 2026/6/28
 */
@Component("userRepository")
public class UserElasticsearchRepositoryImpl extends AbstractUserRepositoryImpl {
}
