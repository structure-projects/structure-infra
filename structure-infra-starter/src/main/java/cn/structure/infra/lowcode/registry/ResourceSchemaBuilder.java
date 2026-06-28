package cn.structure.infra.lowcode.registry;

import cn.structure.infra.lowcode.model.*;
import cn.structure.infra.lowcode.properties.LowCodeProperties;

import java.util.concurrent.TimeUnit;

/**
 * 资源 Schema 构建器
 * <p>
 * 负责将配置属性（{@link LowCodeProperties}）转换为框架内部使用的
 * {@link ResourceSchema} 和 {@link RepositoryConfig} 模型对象。
 * <p>
 * 主要完成字符串配置值到枚举类型的转换，以及默认值的填充。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
public class ResourceSchemaBuilder {

    /**
     * 从配置属性构建资源 Schema
     *
     * @param resourceName 资源名称
     * @param schemaProps  Schema 配置属性
     * @return 资源 Schema
     */
    public static ResourceSchema buildSchema(String resourceName, LowCodeProperties.SchemaProperties schemaProps) {
        ResourceSchema schema = new ResourceSchema();
        schema.setResourceName(resourceName);
        schema.setTableName(schemaProps.getTableName() != null ? schemaProps.getTableName() : resourceName);

        if (schemaProps.getFields() != null) {
            for (var entry : schemaProps.getFields().entrySet()) {
                String fieldName = entry.getKey();
                LowCodeProperties.FieldProperties fieldProps = entry.getValue();
                FieldSchema field = buildField(fieldName, fieldProps);
                schema.addField(field);
            }
        }

        return schema;
    }

    /**
     * 从配置属性构建字段 Schema
     *
     * @param fieldName  字段名称
     * @param fieldProps 字段配置属性
     * @return 字段 Schema
     */
    public static FieldSchema buildField(String fieldName, LowCodeProperties.FieldProperties fieldProps) {
        FieldSchema field = new FieldSchema();
        field.setName(fieldName);
        field.setType(parseFieldType(fieldProps.getType()));
        field.setLength(fieldProps.getLength());
        field.setPrecision(fieldProps.getPrecision());
        field.setScale(fieldProps.getScale());
        field.setPrimaryKey(fieldProps.isPrimaryKey());
        field.setAutoIncrement(fieldProps.isAutoIncrement());
        field.setNullable(fieldProps.isNullable());
        field.setUnique(fieldProps.isUnique());
        field.setIndex(fieldProps.isIndex());
        field.setDefaultValue(fieldProps.getDefaultValue());
        field.setAutoFill(parseAutoFillType(fieldProps.getAutoFill()));
        field.setDescription(fieldProps.getDescription());
        return field;
    }

    /**
     * 从配置属性构建仓储配置
     *
     * @param repoProps 仓储配置属性
     * @return 仓储配置
     */
    public static RepositoryConfig buildRepositoryConfig(LowCodeProperties.RepositoryProperties repoProps) {
        RepositoryConfig config = new RepositoryConfig();
        config.setType(parseStorageType(repoProps.getType()));
        config.setDatasource(repoProps.getDatasource());

        if (repoProps.getCqrs() != null) {
            CqrsConfig cqrsConfig = new CqrsConfig();
            cqrsConfig.setEnabled(repoProps.getCqrs().isEnabled());
            cqrsConfig.setReadType(parseStorageType(repoProps.getCqrs().getReadType()));
            cqrsConfig.setReadDatasource(repoProps.getCqrs().getReadDatasource());
            config.setCqrs(cqrsConfig);
        }

        if (repoProps.getCache() != null) {
            CacheConfig cacheConfig = new CacheConfig();
            cacheConfig.setEnabled(repoProps.getCache().isEnabled());
            cacheConfig.setTtl(repoProps.getCache().getTtl());
            cacheConfig.setTimeUnit(parseTimeUnit(repoProps.getCache().getTimeUnit()));
            config.setCache(cacheConfig);
        }

        return config;
    }

    /**
     * 解析字段类型字符串为枚举
     *
     * @param type 类型字符串
     * @return 字段类型枚举
     */
    private static FieldType parseFieldType(String type) {
        if (type == null) return FieldType.STRING;
        return switch (type.toLowerCase()) {
            case "string", "varchar" -> FieldType.STRING;
            case "long", "bigint" -> FieldType.LONG;
            case "int", "integer" -> FieldType.INTEGER;
            case "bool", "boolean" -> FieldType.BOOLEAN;
            case "decimal", "double", "float" -> FieldType.DECIMAL;
            case "datetime", "timestamp" -> FieldType.DATETIME;
            case "date" -> FieldType.DATE;
            case "objectid", "object_id" -> FieldType.OBJECT_ID;
            case "text" -> FieldType.TEXT;
            case "json" -> FieldType.JSON;
            default -> FieldType.STRING;
        };
    }

    /**
     * 解析存储类型字符串为枚举
     *
     * @param type 类型字符串
     * @return 存储类型枚举
     */
    private static StorageType parseStorageType(String type) {
        if (type == null) return StorageType.MYSQL;
        return switch (type.toLowerCase()) {
            case "mysql" -> StorageType.MYSQL;
            case "mongodb", "mongo" -> StorageType.MONGODB;
            case "elasticsearch", "es" -> StorageType.ELASTICSEARCH;
            case "redis" -> StorageType.REDIS;
            case "memory", "in_memory" -> StorageType.IN_MEMORY;
            default -> StorageType.MYSQL;
        };
    }

    /**
     * 解析自动填充类型字符串为枚举
     *
     * @param type 类型字符串
     * @return 自动填充类型枚举
     */
    private static AutoFillType parseAutoFillType(String type) {
        if (type == null) return AutoFillType.NONE;
        return switch (type.toLowerCase()) {
            case "create", "insert" -> AutoFillType.CREATE;
            case "update" -> AutoFillType.UPDATE;
            case "create_update", "insert_update", "both" -> AutoFillType.CREATE_UPDATE;
            default -> AutoFillType.NONE;
        };
    }

    /**
     * 解析时间单位字符串为枚举
     *
     * @param unit 单位字符串
     * @return 时间单位枚举
     */
    private static TimeUnit parseTimeUnit(String unit) {
        if (unit == null) return TimeUnit.SECONDS;
        return switch (unit.toLowerCase()) {
            case "seconds", "second", "s" -> TimeUnit.SECONDS;
            case "minutes", "minute", "m" -> TimeUnit.MINUTES;
            case "hours", "hour", "h" -> TimeUnit.HOURS;
            case "days", "day", "d" -> TimeUnit.DAYS;
            case "milliseconds", "ms" -> TimeUnit.MILLISECONDS;
            default -> TimeUnit.SECONDS;
        };
    }
}
