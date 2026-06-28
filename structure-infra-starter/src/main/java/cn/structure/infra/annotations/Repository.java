package cn.structure.infra.annotations;

import cn.structure.infra.repository.RepositoryType;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Repository {

    /**
     * 仓储名称
     *
     * @return
     */
    String value() default "";

    /**
     * 仓储类型 默认自动
     *
     * @return
     */
    RepositoryType type() default RepositoryType.AUTO;


    /**
     * 实体类
     *
     * @return
     */
    Class<?> entity() default Object.class;

    /**
     * PO持久化对象类型
     * <p>
     * 用于 RepositoryFacade 中的 Entity <-> PO 转换
     *
     * @return
     */
    Class<?> po() default Object.class;

    /**
     * 主键类型
     * <p>
     * 默认 Long，如果需要指定其他类型可配置
     *
     * @return
     */
    Class<?> id() default Long.class;

    /**
     * 仓储描述
     *
     * @return
     */
    String description() default "";


    /**
     * 是否缓存
     *
     * @return
     */
    boolean cache() default false;

    /**
     * 缓存时间
     *
     * @return
     */
    long cacheTime() default 60L;

    /**
     * 缓存时间单位
     *
     * @return
     */
    TimeUnit cacheTimeUnit() default TimeUnit.SECONDS;

    /**
     * 是否启用 CQRS 读写分离
     * <p>
     * 启用后，读操作使用 readDelegate，写操作使用 baseDelegate
     * <p>
     * 必须与 readDelegateClass 配合使用，两者同时成立时才启用读代理
     *
     * @return true 启用 CQRS
     */
    boolean cqrs() default false;

    /**
     * 读代理类
     * <p>
     * 指定读操作使用的代理类，用于 CQRS 读写分离
     * <p>
     * 必须与 cqrs=true 配合使用，两者同时成立时才启用读代理
     *
     * @return 读代理类
     */
    Class<?> readDelegateClass() default Object.class;

}
