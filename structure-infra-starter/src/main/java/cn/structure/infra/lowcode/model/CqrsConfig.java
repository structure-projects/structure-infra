package cn.structure.infra.lowcode.model;

import lombok.Data;

/**
 * CQRS 配置
 * <p>
 * 定义读写分离的配置，启用后读操作优先使用读存储，写操作使用基础存储，
 * 读存储异常时自动回退到基础存储。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@Data
public class CqrsConfig {

    /**
     * 是否启用 CQRS 读写分离
     */
    private boolean enabled;

    /**
     * 读存储类型
     */
    private StorageType readType;

    /**
     * 读数据源名称
     */
    private String readDatasource;
}
