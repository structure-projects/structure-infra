package cn.structure.infra.repository;

import cn.structure.infra.annotations.Repository;
import cn.structure.infra.repository.RepositoryType;
import lombok.Data;

import java.util.concurrent.TimeUnit;

/**
 * 仓储定义元数据
 * <p>
 * 封装 @Repository 注解的配置信息
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Data
public class RepositoryDefinition {

    /**
     * 仓储名称（Bean名称）
     */
    private String name;

    /**
     * 仓储类型
     */
    private RepositoryType type;

    /**
     * 实体类类型
     */
    private Class<?> entityClass;

    /**
     * PO持久化对象类型
     */
    private Class<?> poClass;

    /**
     * 主键类型
     */
    private Class<?> idClass;

    /**
     * 仓储描述
     */
    private String description;

    /**
     * 是否启用缓存
     */
    private boolean cache;

    /**
     * 缓存时间
     */
    private long cacheTime;

    /**
     * 缓存时间单位
     */
    private TimeUnit cacheTimeUnit;

    /**
     * 原始注解
     */
    private Repository annotation;

    /**
     * 从注解构建仓储定义
     *
     * @param annotation @Repository注解实例
     * @param beanName   Bean名称
     * @return 仓储定义
     */
    public static RepositoryDefinition fromAnnotation(Repository annotation, String beanName) {
        RepositoryDefinition definition = new RepositoryDefinition();
        definition.setName(beanName);
        definition.setType(annotation.type());
        definition.setEntityClass(annotation.entity());
        definition.setPoClass(annotation.po());
        definition.setIdClass(annotation.id());
        definition.setDescription(annotation.description());
        definition.setCache(annotation.cache());
        definition.setCacheTime(annotation.cacheTime());
        definition.setCacheTimeUnit(annotation.cacheTimeUnit());
        definition.setAnnotation(annotation);
        return definition;
    }

    /**
     * 验证配置是否有效
     *
     * @return true if valid
     */
    public boolean isValid() {
        return entityClass != null && entityClass != Object.class
                && poClass != null && poClass != Object.class;
    }

}
