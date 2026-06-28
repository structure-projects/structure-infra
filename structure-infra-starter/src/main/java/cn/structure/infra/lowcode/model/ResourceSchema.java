package cn.structure.infra.lowcode.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 资源 Schema 定义
 * <p>
 * 描述低代码资源的完整元数据，包括资源名称、表名、主键信息和所有字段定义。
 * 作为低代码仓储的核心元数据模型，框架根据 Schema 自动生成 DDL 和 DML 语句。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@Data
public class ResourceSchema {

    /**
     * 资源名称（唯一标识，用于路由查找）
     */
    private String resourceName;

    /**
     * 表名/集合名/索引名（对应存储引擎中的数据容器名称）
     */
    private String tableName;

    /**
     * 主键字段名
     */
    private String idFieldName;

    /**
     * 主键类型
     */
    private FieldType idType;

    /**
     * 字段定义集合（保持插入顺序）
     */
    private Map<String, FieldSchema> fields = new LinkedHashMap<>();

    /**
     * 获取主键字段定义
     *
     * @return 主键字段 schema，未设置时返回 null
     */
    public FieldSchema getIdField() {
        return fields.get(idFieldName);
    }

    /**
     * 获取指定名称的字段定义
     *
     * @param fieldName 字段名称
     * @return 字段 schema，不存在时返回 null
     */
    public FieldSchema getField(String fieldName) {
        return fields.get(fieldName);
    }

    /**
     * 添加字段定义
     * <p>
     * 如果字段是主键，会自动设置 idFieldName 和 idType。
     *
     * @param field 字段定义
     */
    public void addField(FieldSchema field) {
        fields.put(field.getName(), field);
        if (field.isPrimaryKey()) {
            idFieldName = field.getName();
            idType = field.getType();
        }
    }
}
