package cn.structure.infra.annotations;

import cn.structure.infra.repository.RepositoryType;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 仓储标记注解
 * <p>
 * 标注在 {@link cn.structure.infra.repository.RepositoryFacade} 的子类上，
 * 用于声明一个领域仓储及其元数据（实体类型、PO 类型、主键类型、缓存策略、CQRS 配置等）。
 * <p>
 * 框架在启动时通过 {@link cn.structure.infra.repository.RepositoryBeanPostProcessor}
 * 扫描此注解，并根据配置自动注入对应的 BASE/READ Delegate。
 * <p>
 * 示例：
 * <pre>
 * &#64;Repository(value = "userRepository", entity = User.class, po = UserPO.class, id = Long.class)
 * public class UserRepository extends RepositoryFacade&lt;User, Long, UserPO, MybatisPlusRepositoryDelegate&lt;UserPO, Long&gt;&gt; {
 * }
 * </pre>
 * <p>
 * 启用 CQRS 读写分离的示例：
 * <pre>
 * &#64;Repository(value = "userRepository", entity = User.class, po = UserPO.class,
 *              cqrs = true, readDelegateClass = ElasticsearchRepositoryDelegate.class)
 * public class UserRepository extends RepositoryFacade&lt;User, Long, UserPO, MybatisPlusRepositoryDelegate&lt;UserPO, Long&gt;&gt; {
 * }
 * </pre>
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Repository {

    /**
     * 仓储名称（对应 Bean 名称，用于与 {@link DelegateFor#name()} 进行匹配）
     *
     * @return 仓储名称，默认空字符串表示使用类名
     */
    String value() default "";

    /**
     * 仓储类型，默认 {@link RepositoryType#AUTO} 由框架自动推断
     * <p>
     * 指定具体类型时，会优先匹配同类型的 Delegate
     *
     * @return 仓储类型
     */
    RepositoryType type() default RepositoryType.AUTO;


    /**
     * 领域实体类类型
     * <p>
     * RepositoryFacade 在执行 Entity ↔ PO 转换时使用
     *
     * @return 实体类，默认 Object.class 表示从泛型参数推断
     */
    Class<?> entity() default Object.class;

    /**
     * PO 持久化对象类型
     * <p>
     * 用于 RepositoryFacade 中的 Entity ↔ PO 转换，以及 Delegate 匹配
     *
     * @return PO 类，默认 Object.class 表示从泛型参数推断
     */
    Class<?> po() default Object.class;

    /**
     * 主键类型
     * <p>
     * 默认 Long，如果需要指定其他类型可配置
     *
     * @return 主键类型
     */
    Class<?> id() default Long.class;

    /**
     * 仓储描述
     *
     * @return 描述信息
     */
    String description() default "";


    /**
     * 是否启用缓存
     *
     * @return true 表示启用缓存
     */
    boolean cache() default false;

    /**
     * 缓存时间
     *
     * @return 缓存过期时间数值
     */
    long cacheTime() default 60L;

    /**
     * 缓存时间单位
     *
     * @return 缓存时间单位
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
