package cn.structure.infra.mongodb.repository;

import cn.structure.infra.repository.RepositoryDelegate;
import cn.structure.infra.repository.RepositoryDelegateFactory;
import cn.structure.infra.repository.RepositoryType;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * MongoDB 仓储委托工厂
 * <p>
 * 实现 {@link RepositoryDelegateFactory} SPI，自动创建 {@link MongoRepositoryDelegate} 实例。
 * 在仓储框架中，当 {@code RepositoryFacade} 找不到用户自定义的 Delegate 时，会通过本工厂按 PO 类型
 * 创建默认 Delegate 实例（依赖容器中的 {@link MongoTemplate}）。
 * <p>
 * 与 {@link MongoDelegateBeanPostProcessor} 的分工：本工厂负责"无自定义 Delegate 时创建默认实现"，
 * BeanPostProcessor 负责"已有自定义子类时补齐依赖"。
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
public class MongoDelegateFactory implements RepositoryDelegateFactory {

    /** MongoDB 操作模板，由容器注入并共享给所有 Delegate 实例 */
    private final MongoTemplate mongoTemplate;

    /**
     * 构造工厂，注入 MongoTemplate。
     *
     * @param mongoTemplate MongoDB 操作模板
     */
    public MongoDelegateFactory(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * 返回该工厂支持的仓储类型，用于 SPI 路由匹配。
     *
     * @return 固定返回 {@link RepositoryType#MONGODB}
     */
    @Override
    public RepositoryType getType() {
        return RepositoryType.MONGODB;
    }

    /**
     * 为指定 PO 类型创建 {@link MongoRepositoryDelegate} 实例。
     * <p>
     * MongoDB 实现不依赖 Mapper 查找，直接以入参 PO 类型构造 Delegate，因此失败概率较低；
     * 出现异常时返回 null，由上层 RepositoryFacade 继续尝试其他工厂或抛出异常。
     *
     * @param poClass PO 实体类型
     * @param idClass 主键类型（当前实现未使用，保留以匹配 SPI 签名）
     * @return 已注入 MongoTemplate 的 Delegate 实例；构造异常时返回 null
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public RepositoryDelegate<?, ?> createDelegate(Class<?> poClass, Class<?> idClass) {
        try {
            return new MongoRepositoryDelegate(mongoTemplate, poClass);
        } catch (Exception e) {
            return null;
        }
    }
}
