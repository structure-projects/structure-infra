package cn.structure.infra.lowcode.model;

/**
 * 自动填充类型枚举
 * <p>
 * 定义字段在数据写入时的自动填充策略，减少重复的字段赋值代码。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
public enum AutoFillType {

    /**
     * 不自动填充
     */
    NONE,

    /**
     * 仅创建时填充
     */
    CREATE,

    /**
     * 仅更新时填充
     */
    UPDATE,

    /**
     * 创建和更新时都填充
     */
    CREATE_UPDATE
}
