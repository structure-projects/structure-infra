package cn.structure.infra.annotations;

import cn.structure.infra.repository.RepositoryType;

import java.lang.annotation.*;

/**
 * 仓储委托实现标记注解
 * <p>
 * 标注在具体的 RepositoryDelegate 实现类上
 * <p>
 * 示例：
 * <pre>
 * &#64;DelegateFor(name = "userRepository", po = UserPO.class)
 * public class UserMybatisPlusDelegate extends MybatisPlusRepositoryDelegate&lt;UserPO, Long&gt; {
 *     // 实现
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
}