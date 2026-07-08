package cn.structure.infra.mybatis.plus.repository;

import cn.structure.infra.repository.RepositoryDelegate;
import cn.structure.infra.repository.RepositoryDelegateFactory;
import cn.structure.infra.repository.RepositoryType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.context.ApplicationContext;

/**
 * MyBatis Plus 仓储委托工厂
 * <p>
 * 实现 {@link RepositoryDelegateFactory} SPI，自动创建 {@link MybatisPlusRepositoryDelegate} 实例。
 * 在仓储框架中，当 {@code RepositoryFacade} 找不到用户自定义的 Delegate 时，会通过本工厂按 PO 类型
 * 查找对应的 {@link BaseMapper}，并创建默认 Delegate 实例。
 * <p>
 * 与 {@link MybatisPlusDelegateBeanPostProcessor} 的分工：本工厂负责"无自定义 Delegate 时创建默认实现"，
 * BeanPostProcessor 负责"已有自定义子类时补齐依赖"。
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
public class MybatisPlusDelegateFactory implements RepositoryDelegateFactory {

    /** Spring 上下文，用于按类型/名称查找 BaseMapper Bean */
    private final ApplicationContext applicationContext;

    /**
     * 构造工厂，注入 Spring 应用上下文。
     *
     * @param applicationContext Spring 应用上下文，用于查找 Mapper Bean
     */
    public MybatisPlusDelegateFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 返回该工厂支持的仓储类型，用于 SPI 路由匹配。
     *
     * @return 固定返回 {@link RepositoryType#MYBATIS_PLUS}
     */
    @Override
    public RepositoryType getType() {
        return RepositoryType.MYBATIS_PLUS;
    }

    /**
     * 为指定 PO 类型创建 {@link MybatisPlusRepositoryDelegate} 实例。
     * <p>
     * 内部按约定（{@code .po.} → {@code .mapper.}、PO 后缀 → Mapper）查找对应 BaseMapper，
     * 找不到时返回 null（由上层 RepositoryFacade 继续尝试其他工厂或抛出异常）。
     *
     * @param poClass PO 实体类型
     * @param idClass 主键类型（当前实现未使用，保留以匹配 SPI 签名）
     * @return 已注入 BaseMapper 的 Delegate 实例；未找到 Mapper 时返回 null
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public RepositoryDelegate<?, ?> createDelegate(Class<?> poClass, Class<?> idClass) {
        try {
            BaseMapper mapper = (BaseMapper) findMapperByPoClass(poClass);
            if (mapper == null) {
                return null;
            }
            return new MybatisPlusRepositoryDelegate(mapper, poClass);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 根据 PO 类查找对应的 BaseMapper Bean。
     * <p>
     * 查找策略（按优先级）：
     * <ol>
     *   <li>约定包名转换：将 {@code xxx.po.XxxPO} 推导为 {@code xxx.mapper.XxxMapper}，按类型获取 Bean</li>
     *   <li>失败时遍历所有 Bean 名称，匹配以简单 Mapper 名（如 {@code XxxMapper}）结尾的 Bean</li>
     * </ol>
     *
     * @param poClass PO 类型
     * @return 对应的 BaseMapper Bean，未找到返回 null
     */
    private Object findMapperByPoClass(Class<?> poClass) {
        // 约定：po 包下的 XxxPO 对应 mapper 包下的 XxxMapper
        String poClassName = poClass.getName();
        String mapperClassName = poClassName.replace(".po.", ".mapper.")
                .replace("PO", "Mapper");
        try {
            Class<?> mapperClass = Class.forName(mapperClassName);
            return applicationContext.getBean(mapperClass);
        } catch (Exception e) {
            // 兜底：遍历 Bean 名称，匹配以 XxxMapper 结尾的 Bean
            String simpleMapperName = poClass.getSimpleName().replace("PO", "Mapper");
            for (String beanName : applicationContext.getBeanDefinitionNames()) {
                if (beanName.endsWith(simpleMapperName)) {
                    return applicationContext.getBean(beanName);
                }
            }
            return null;
        }
    }
}
