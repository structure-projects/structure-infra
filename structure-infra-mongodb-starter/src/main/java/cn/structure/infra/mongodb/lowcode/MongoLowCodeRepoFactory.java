package cn.structure.infra.mongodb.lowcode;

import cn.structure.infra.lowcode.model.RepositoryConfig;
import cn.structure.infra.lowcode.model.ResourceSchema;
import cn.structure.infra.lowcode.model.StorageType;
import cn.structure.infra.lowcode.repository.LowCodeRepoFactory;
import cn.structure.infra.lowcode.repository.LowCodeStorage;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * MongoDB 低代码仓储工厂
 * <p>
 * 负责创建 MongoDB 类型的低代码存储实例，内部使用 MongoTemplate + Document 执行动态操作。
 * <p>
 * 核心特性：
 * <ul>
 *   <li>Document 动态操作：使用 Document 代替 POJO，无需定义实体类</li>
 *   <li>自动创建集合：初始化时自动创建集合和索引</li>
 *   <li>自动填充：支持创建时间、更新时间自动填充</li>
 * </ul>
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
public class MongoLowCodeRepoFactory implements LowCodeRepoFactory {

    private final MongoTemplate mongoTemplate;

    /**
     * 通过 MongoTemplate 构造
     *
     * @param mongoTemplate MongoTemplate 实例
     */
    public MongoLowCodeRepoFactory(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public StorageType getType() {
        return StorageType.MONGODB;
    }

    @Override
    public LowCodeStorage createStorage(ResourceSchema schema, RepositoryConfig config) {
        return new MongoLowCodeStorage(schema, mongoTemplate);
    }
}