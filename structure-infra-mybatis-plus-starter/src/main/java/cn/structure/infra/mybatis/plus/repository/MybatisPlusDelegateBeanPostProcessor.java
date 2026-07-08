package cn.structure.infra.mybatis.plus.repository;

import cn.structure.infra.annotations.DelegateFor;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * MyBatis Plus RepositoryDelegate 的 BeanPostProcessor，负责为用户自定义 Delegate 子类自动注入 BaseMapper。
 * <p>
 * 在仓储框架中，业务方可继承 {@link MybatisPlusRepositoryDelegate} 实现自定义 Delegate，并通过
 * {@link DelegateFor} 注解声明其服务的 PO 类型。本后处理器在 Bean 初始化完成后：
 * <ol>
 *   <li>识别所有 {@link MybatisPlusRepositoryDelegate} 类型的 Bean</li>
 *   <li>读取其 {@link DelegateFor#po()} 指定的 PO 类型</li>
 *   <li>按 PO 包名约定（{@code .po.} → {@code .mapper.}、PO 后缀 → Mapper）或 Bean 名称查找对应 BaseMapper</li>
 *   <li>通过 setter 反向注入 BaseMapper 与 PO 类型，使自定义 Delegate 可正常工作</li>
 * </ol>
 * <p>
 * 与 {@link MybatisPlusDelegateFactory} 的分工：工厂负责"无自定义 Delegate 时自动创建"，
 * 本处理器负责"已有自定义 Delegate 时补齐依赖"，二者协同保证 RepositoryFacade 总能拿到可用的 Delegate。
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Slf4j
public class MybatisPlusDelegateBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    /** Spring 上下文，用于按类型或名称查找 BaseMapper Bean */
    private ApplicationContext applicationContext;

    /**
     * 注入 Spring 应用上下文，供后续按类型/名称查询 Bean。
     *
     * @param applicationContext Spring 应用上下文
     * @throws BeansException 上下文注入异常
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 在 Bean 初始化完成后，对自定义 MybatisPlusRepositoryDelegate 实现类注入 BaseMapper。
     * <p>
     * 仅当 Bean 同时满足：是 {@link MybatisPlusRepositoryDelegate} 实例、且类上标注了
     * {@link DelegateFor} 注解、注解显式指定了 PO 类型时，才执行注入。
     *
     * @param bean     待处理的 Bean 实例
     * @param beanName Bean 名称
     * @return 原始 Bean（已注入依赖），未匹配类型时原样返回
     * @throws BeansException 处理过程中的异常
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof MybatisPlusRepositoryDelegate) {
            MybatisPlusRepositoryDelegate delegate = (MybatisPlusRepositoryDelegate) bean;
            // 读取 @DelegateFor 注解，识别该 Delegate 服务的 PO 类型
            DelegateFor annotation = bean.getClass().getAnnotation(DelegateFor.class);
            if (annotation != null && annotation.po() != void.class) {
                try {
                    // 按 PO 类型约定查找对应的 BaseMapper
                    Object mapper = findMapperByPoClass(annotation.po());
                    if (mapper != null) {
                        // 反向注入 BaseMapper 与 PO 类型，使自定义 Delegate 可正常工作
                        delegate.setBaseMapper((BaseMapper) mapper);
                        delegate.setEntityClass(annotation.po());
                        log.info("Injected BaseMapper into MybatisPlusRepositoryDelegate: {}", beanName);
                    } else {
                        log.warn("No BaseMapper found for PO class {} in MybatisPlusRepositoryDelegate {}", annotation.po().getSimpleName(), beanName);
                    }
                } catch (Exception e) {
                    log.warn("Failed to inject BaseMapper into MybatisPlusRepositoryDelegate {}: {}", beanName, e.getMessage());
                }
            }
        }
        return bean;
    }

    /**
     * 根据 PO 类查找对应的 BaseMapper Bean。
     * <p>
     * 查找策略（按优先级）：
     * <ol>
     *   <li>约定包名转换：将 {@code xxx.po.XxxPO} 推导为 {@code xxx.mapper.XxxMapper}，按类型获取 Bean</li>
     *   <li>若类型不存在，再用简单名（如 {@code XxxMapper}）按 Bean 名称获取</li>
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
        } catch (ClassNotFoundException e) {
            log.debug("Mapper class not found: {}", mapperClassName);
        } catch (Exception e) {
            log.debug("Failed to get mapper bean: {}", e.getMessage());
        }

        // 兜底：按 Bean 简单名查找（如 "XxxMapper"）
        String simpleMapperName = poClass.getSimpleName().replace("PO", "Mapper");
        try {
            return applicationContext.getBean(simpleMapperName);
        } catch (Exception e) {
            log.debug("Failed to get mapper by name: {}", simpleMapperName);
        }

        return null;
    }
}