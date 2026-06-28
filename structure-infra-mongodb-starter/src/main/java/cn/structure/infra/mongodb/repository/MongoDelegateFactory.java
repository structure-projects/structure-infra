package cn.structure.infra.mongodb.repository;

import cn.structure.infra.repository.RepositoryDelegate;
import cn.structure.infra.repository.RepositoryDelegateFactory;
import cn.structure.infra.repository.RepositoryType;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * MongoDB 仓储委托工厂
 * <p>
 * 自动创建 MongoRepositoryDelegate 实例
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
public class MongoDelegateFactory implements RepositoryDelegateFactory {

    private final MongoTemplate mongoTemplate;

    public MongoDelegateFactory(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public RepositoryType getType() {
        return RepositoryType.MONGODB;
    }

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
