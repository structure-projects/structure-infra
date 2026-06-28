package cn.structure.infra.lowcode.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段 Schema 定义
 * <p>
 * 描述低代码资源中单个字段的元数据信息，包括字段名称、类型、约束、
 * 默认值、自动填充策略等。框架根据这些信息自动生成 DDL 和操作 SQL。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldSchema {

    /**
     * 字段名称（Java 侧使用的名称）
     */
    private String name;

    /**
     * 数据库列名（为空时默认与 name 相同）
     */
    private String columnName;

    /**
     * 字段类型
     */
    private FieldType type;

    /**
     * 字段长度（字符串类型使用）
     */
    @Builder.Default
    private int length = 255;

    /**
     * 精度（十进制类型使用，总位数）
     */
    @Builder.Default
    private int precision = 10;

    /**
     * 小数位数（十进制类型使用）
     */
    @Builder.Default
    private int scale = 2;

    /**
     * 是否为主键
     */
    private boolean primaryKey;

    /**
     * 是否自增（仅数值类型主键有效）
     */
    private boolean autoIncrement;

    /**
     * 是否允许为空
     */
    @Builder.Default
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
     * 自动填充策略
     */
    @Builder.Default
    private AutoFillType autoFill = AutoFillType.NONE;

    /**
     * 字段描述
     */
    private String description;
}
