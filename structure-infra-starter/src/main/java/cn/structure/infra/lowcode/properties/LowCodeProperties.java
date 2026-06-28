package cn.structure.infra.lowcode.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 低代码仓储配置属性
 * <p>
 * 对应 YAML 配置前缀：{@code structure.infra.lowcode}
 * <p>
 * 支持通过配置文件定义低代码资源，包括资源的 schema 结构和仓储配置。
 * 框架启动时自动加载这些配置并注册对应的低代码仓储。
 * <p>
 * 配置示例：
 * <pre>{@code
 * structure:
 *   infra:
 *     lowcode:
 *       enabled: true
 *       resources:
 *         user:
 *           schema:
 *             table-name: t_user
 *             fields:
 *               id:
 *                 type: long
 *                 primary-key: true
 *                 auto-increment: true
 *               username:
 *                 type: string
 *                 length: 64
 *                 nullable: false
 *                 index: true
 *           repository:
 *             type: mysql
 * }</pre>
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@Data
@ConfigurationProperties(prefix = "structure.infra.lowcode")
public class LowCodeProperties {

    /**
     * 是否启用低代码仓储
     */
    private boolean enabled = true;

    /**
     * 资源定义 Map（资源名 -> 资源配置）
     */
    private Map<String, ResourceProperties> resources = new LinkedHashMap<>();

    /**
     * 单个资源配置
     */
    @Data
    public static class ResourceProperties {

        /**
         * 数据结构定义
         */
        private SchemaProperties schema;

        /**
         * 仓储配置
         */
        private RepositoryProperties repository;
    }

    /**
     * 数据结构配置
     */
    @Data
    public static class SchemaProperties {

        /**
         * 表名/集合名
         */
        private String tableName;

        /**
         * 主键类型（long/string/objectId 等）
         */
        private String idType = "long";

        /**
         * 字段定义 Map（字段名 -> 字段配置）
         */
        private Map<String, FieldProperties> fields = new LinkedHashMap<>();
    }

    /**
     * 字段配置
     */
    @Data
    public static class FieldProperties {

        /**
         * 字段类型（string/long/int/boolean/decimal/datetime/date/text/json）
         */
        private String type = "string";

        /**
         * 字段长度（字符串类型有效）
         */
        private int length = 255;

        /**
         * 精度（十进制类型有效，总位数）
         */
        private int precision = 10;

        /**
         * 小数位数（十进制类型有效）
         */
        private int scale = 2;

        /**
         * 是否主键
         */
        private boolean primaryKey;

        /**
         * 是否自增（仅数值主键有效）
         */
        private boolean autoIncrement;

        /**
         * 是否允许为空
         */
        private boolean nullable = true;

        /**
         * 是否唯一约束
         */
        private boolean unique;

        /**
         * 是否创建索引
         */
        private boolean index;

        /**
         * 默认值
         */
        private String defaultValue;

        /**
         * 自动填充策略（none/create/update/create_update）
         */
        private String autoFill = "none";

        /**
         * 字段描述
         */
        private String description;
    }

    /**
     * 仓储配置
     */
    @Data
    public static class RepositoryProperties {

        /**
         * 存储类型（mysql/mongodb/elasticsearch/redis）
         */
        private String type = "mysql";

        /**
         * 数据源名称
         */
        private String datasource;

        /**
         * CQRS 读写分离配置
         */
        private CqrsProperties cqrs;

        /**
         * 缓存配置
         */
        private CacheProperties cache;
    }

    /**
     * CQRS 配置
     */
    @Data
    public static class CqrsProperties {

        /**
         * 是否启用
         */
        private boolean enabled;

        /**
         * 读存储类型
         */
        private String readType;

        /**
         * 读数据源名称
         */
        private String readDatasource;
    }

    /**
     * 缓存配置
     */
    @Data
    public static class CacheProperties {

        /**
         * 是否启用
         */
        private boolean enabled;

        /**
         * 过期时间
         */
        private long ttl = 300;

        /**
         * 时间单位（seconds/minutes/hours/days）
         */
        private String timeUnit = "seconds";
    }
}
