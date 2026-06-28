package cn.structure.infra.annotations;

import cn.structure.infra.repository.DelegateType;
import cn.structure.infra.repository.RepositoryType;

import java.lang.annotation.*;

/**
 * 仓储委托实现标记注解
 * <p>
 * 标注在具体的 RepositoryDelegate 实现类上
 * <p>
 * 示例：
 * <pre>
 * &#64;DelegateFor(name = "userRepository", po = UserPO.class, delegateType = DelegateType.BASE)
 * public class UserMybatisPlusDelegate extends MybatisPlusRepositoryDelegate&lt;UserPO, Long&gt; {
 *     // 实现
 * }
 * </pre>
 * <p>
 * CQRS 模式下可以指定读代理：
 * <pre>
 * &#64;DelegateFor(name = "userRepository", po = UserPO.class, delegateType = DelegateType.READ)
 * public class UserReadDelegate extends ElasticsearchRepositoryDelegate&lt;UserPO, Long&gt; {
 *     // 读操作实现
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
@Documented
public @interface DelegateFor {

    /**
     * 仓储名称，对应 RepositoryFacade 的 Bean 名称
     *
     * @return 仓储名称
     */
    String name() default "";

    /**
     * 存储类型
     *
     * @return 仓储类型
     */
    RepositoryType type() default RepositoryType.AUTO;

    /**
     * 持久化对象类型
     *
     * @return PO 类
     */
    Class<?> po() default Object.class;

    /**
     * 描述
     *
     * @return 描述信息
     */
    String description() default "";

    /**
     * 优先级，多个同类型 Delegate 时使用优先级高的
     *
     * @return 优先级，数字越大优先级越高
     */
    int priority() default 0;

    /**
     * 委托类型
     * <p>
     * - BASE: 基础代理，承担写操作和默认读操作
     * - READ: 读代理，专门承担读操作（CQRS 模式下使用）
     *
     * @return 委托类型
     */
    DelegateType delegateType() default DelegateType.BASE;
}