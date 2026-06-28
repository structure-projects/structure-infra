package cn.structure.infra.lowcode.router;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.lowcode.model.RepositoryConfig;
import cn.structure.infra.lowcode.model.ResourceSchema;
import cn.structure.infra.lowcode.model.StorageType;
import cn.structure.infra.lowcode.repository.LowCodeRepoFactory;
import cn.structure.infra.lowcode.repository.LowCodeRepository;
import cn.structure.infra.lowcode.repository.LowCodeStorage;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 低代码仓储路由引擎
 * <p>
 * 低代码仓储体系的核心调度器，实现 {@link LowCodeRepository} 接口，
 * 负责将资源名称路由到对应的存储实现，并提供 CQRS 读写分离和回退机制。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>资源注册：接收资源定义，创建对应的存储实例</li>
 *   <li>路由调度：根据资源名称查找对应的存储实例</li>
 *   <li>CQRS 读写分离：读操作优先使用读存储，写操作使用基础存储</li>
 *   <li>故障回退：读存储异常时自动回退到基础存储</li>
 * </ul>
 * <p>
 * 与 {@link cn.structure.infra.repository.RepositoryFacade} 的设计理念一致，
 * 区别在于低代码体系使用资源名称而非泛型类型来标识操作对象。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@Slf4j
public class LowCodeRepositoryRouter implements LowCodeRepository {

    /**
     * 存储持有者注册表（资源名 -> 存储持有者）
     */
    private final Map<String, StorageHolder> storageRegistry = new ConcurrentHashMap<>();

    /**
     * 仓储工厂映射（存储类型 -> 工厂）
     */
    private final Map<StorageType, LowCodeRepoFactory> factoryMap;

    /**
     * 构造函数
     *
     * @param factories 所有可用的仓储工厂列表
     */
    public LowCodeRepositoryRouter(List<LowCodeRepoFactory> factories) {
        this.factoryMap = new ConcurrentHashMap<>();
        for (LowCodeRepoFactory factory : factories) {
            this.factoryMap.put(factory.getType(), factory);
        }
    }

    /**
     * 注册资源
     * <p>
     * 根据资源 schema 和仓储配置创建对应的存储实例，并初始化存储容器（建表/建集合）。
     * 如果配置了 CQRS 读写分离，会同时创建读存储实例。
     *
     * @param resourceName 资源名称
     * @param schema       资源 schema 定义
     * @param config       仓储配置
     */
    public void registerResource(String resourceName, ResourceSchema schema, RepositoryConfig config) {
        LowCodeStorage baseStorage = createStorage(schema, config.getType(), config);
        LowCodeStorage readStorage = null;

        if (config.isCqrsEnabled() && config.getCqrs().getReadType() != null) {
            try {
                RepositoryConfig readConfig = new RepositoryConfig();
                readConfig.setType(config.getCqrs().getReadType());
                readConfig.setDatasource(config.getCqrs().getReadDatasource());
                readStorage = createStorage(schema, config.getCqrs().getReadType(), readConfig);
            } catch (Exception e) {
                log.warn("Failed to create read storage for resource {}, falling back to base: {}",
                        resourceName, e.getMessage());
            }
        }

        StorageHolder holder = new StorageHolder(schema, config, baseStorage, readStorage);
        storageRegistry.put(resourceName, holder);

        try {
            baseStorage.initialize();
            if (readStorage != null && readStorage != baseStorage) {
                readStorage.initialize();
            }
        } catch (Exception e) {
            log.warn("Failed to initialize storage for resource {}: {}", resourceName, e.getMessage());
        }
    }

    /**
     * 创建存储实例
     *
     * @param schema 资源 schema
     * @param type   存储类型
     * @param config 仓储配置
     * @return 存储实例
     */
    private LowCodeStorage createStorage(ResourceSchema schema, StorageType type, RepositoryConfig config) {
        LowCodeRepoFactory factory = factoryMap.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("No LowCodeRepoFactory found for type: " + type);
        }
        return factory.createStorage(schema, config);
    }

    /**
     * 获取存储持有者
     *
     * @param resourceName 资源名称
     * @return 存储持有者
     * @throws IllegalArgumentException 资源未注册时抛出
     */
    private StorageHolder getHolder(String resourceName) {
        StorageHolder holder = storageRegistry.get(resourceName);
        if (holder == null) {
            throw new IllegalArgumentException("Resource not registered: " + resourceName);
        }
        return holder;
    }

    /**
     * 执行读操作（带 CQRS 路由和回退）
     * <p>
     * 优先使用读存储执行，失败时回退到基础存储。
     * 参考 {@link cn.structure.infra.repository.RepositoryFacade#executeReadOperation} 的设计。
     *
     * @param resourceName      资源名称
     * @param readOperation     读存储操作
     * @param fallbackOperation 基础存储回退操作
     * @param <R>               返回类型
     * @return 操作结果
     */
    private <R> R executeRead(String resourceName,
                              Function<LowCodeStorage, R> readOperation,
                              Function<LowCodeStorage, R> fallbackOperation) {
        StorageHolder holder = getHolder(resourceName);
        if (holder.readStorage != null && holder.config.isCqrsEnabled()) {
            try {
                return readOperation.apply(holder.readStorage);
            } catch (Exception e) {
                log.warn("Read storage operation failed for resource {}, falling back to base: {}",
                        resourceName, e.getMessage());
            }
        }
        return fallbackOperation.apply(holder.baseStorage);
    }

    /**
     * 执行写操作（有返回值）
     * <p>
     * 写操作始终走基础存储。
     *
     * @param resourceName  资源名称
     * @param writeOperation 写操作函数
     * @param <R>           返回类型
     * @return 操作结果
     */
    private <R> R executeWrite(String resourceName, Function<LowCodeStorage, R> writeOperation) {
        StorageHolder holder = getHolder(resourceName);
        return writeOperation.apply(holder.baseStorage);
    }

    /**
     * 执行写操作（无返回值）
     *
     * @param resourceName   资源名称
     * @param writeOperation 写操作消费者
     */
    private void executeWriteVoid(String resourceName, java.util.function.Consumer<LowCodeStorage> writeOperation) {
        StorageHolder holder = getHolder(resourceName);
        writeOperation.accept(holder.baseStorage);
    }

    @Override
    public Map<String, Object> save(String resourceName, Map<String, Object> data) {
        return executeWrite(resourceName, storage -> storage.save(data));
    }

    @Override
    public void removeById(String resourceName, Object id) {
        executeWriteVoid(resourceName, storage -> storage.removeById(id));
    }

    @Override
    public Map<String, Object> findById(String resourceName, Object id) {
        StorageHolder holder = getHolder(resourceName);
        return holder.baseStorage.findById(id);
    }

    @Override
    public Map<String, Object> queryById(String resourceName, Object id) {
        return executeRead(resourceName,
                storage -> storage.queryById(id),
                storage -> storage.queryById(id));
    }

    @Override
    public Optional<Map<String, Object>> queryByIdOptional(String resourceName, Object id) {
        return executeRead(resourceName,
                storage -> storage.queryOneOptional(buildIdQuery(resourceName, id)),
                storage -> storage.queryOneOptional(buildIdQuery(resourceName, id)));
    }

    /**
     * 构建 ID 查询条件 Map
     *
     * @param resourceName 资源名称
     * @param id           主键值
     * @return 查询条件 Map
     */
    private Map<String, Object> buildIdQuery(String resourceName, Object id) {
        StorageHolder holder = getHolder(resourceName);
        return Map.of(holder.schema.getIdFieldName(), id);
    }

    @Override
    public Map<String, Object> queryOne(String resourceName, Map<String, Object> queryParams) {
        return executeRead(resourceName,
                storage -> storage.queryOne(queryParams),
                storage -> storage.queryOne(queryParams));
    }

    @Override
    public Optional<Map<String, Object>> queryOneOptional(String resourceName, Map<String, Object> queryParams) {
        return executeRead(resourceName,
                storage -> storage.queryOneOptional(queryParams),
                storage -> storage.queryOneOptional(queryParams));
    }

    @Override
    public List<Map<String, Object>> queryList(String resourceName, Map<String, Object> queryParams) {
        return executeRead(resourceName,
                storage -> storage.queryList(queryParams),
                storage -> storage.queryList(queryParams));
    }

    @Override
    public ResPage<Map<String, Object>> queryPage(String resourceName, ReqPage reqPage) {
        return executeRead(resourceName,
                storage -> storage.queryPage(reqPage),
                storage -> storage.queryPage(reqPage));
    }

    @Override
    public List<Map<String, Object>> saveBatch(String resourceName, List<Map<String, Object>> dataList) {
        return executeWrite(resourceName, storage -> storage.saveBatch(dataList));
    }

    @Override
    public void removeBatchByIds(String resourceName, List<Object> ids) {
        executeWriteVoid(resourceName, storage -> storage.removeBatchByIds(ids));
    }

    @Override
    public List<Map<String, Object>> listByIds(String resourceName, List<Object> ids) {
        return executeRead(resourceName,
                storage -> storage.listByIds(ids),
                storage -> storage.listByIds(ids));
    }

    @Override
    public long count(String resourceName, Map<String, Object> queryParams) {
        return executeRead(resourceName,
                storage -> storage.count(queryParams),
                storage -> storage.count(queryParams));
    }

    @Override
    public boolean exists(String resourceName, Map<String, Object> queryParams) {
        StorageHolder holder = getHolder(resourceName);
        return holder.baseStorage.exists(queryParams);
    }

    /**
     * 存储持有者内部类
     * <p>
     * 封装一个资源的所有存储相关对象，包括 schema、配置、基础存储和读存储。
     */
    @Setter
    private static class StorageHolder {
        ResourceSchema schema;
        RepositoryConfig config;
        LowCodeStorage baseStorage;
        LowCodeStorage readStorage;

        StorageHolder(ResourceSchema schema, RepositoryConfig config,
                      LowCodeStorage baseStorage, LowCodeStorage readStorage) {
            this.schema = schema;
            this.config = config;
            this.baseStorage = baseStorage;
            this.readStorage = readStorage;
        }
    }
}
