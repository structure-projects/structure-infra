package cn.structure.infra.repository;

import cn.structure.infra.annotations.DelegateFor;
import cn.structure.infra.annotations.Repository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 仓储 Bean 后处理器
 * <p>
 * 仓储框架的核心装配器，负责在 Spring 容器启动过程中完成 Delegate 收集、Facade 识别
 * 以及 Delegate → Facade 的自动注入。是 Facade + Delegate 模式的"装配枢纽"。
 * <p>
 * 工作流程分为三个阶段：
 * <ol>
 *   <li><b>收集阶段</b>（{@link #postProcessBeforeInitialization}）：
 *       扫描所有带 {@link DelegateFor} 注解的 Bean，提取 Delegate 元信息并按 priority 降序排序</li>
 *   <li><b>识别阶段</b>（{@link #postProcessAfterInitialization}）：
 *       识别所有 {@link RepositoryFacade} 实例并记录其 Bean 名称</li>
 *   <li><b>注入阶段</b>（{@link #onApplicationEvent}）：
 *       容器刷新后，对每个 Facade 执行 6 步匹配查找 BASE Delegate，
 *       并在 CQRS 模式下查找 READ Delegate，完成注入</li>
 * </ol>
 * <p>
 * 当无任何匹配的 Delegate 时，依次尝试：
 * <ol>
 *   <li>通过 {@link RepositoryDelegateFactory} 自动创建</li>
 *   <li>回退到 {@link InMemoryRepositoryDelegate}（仅用于开发/测试）</li>
 * </ol>
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Slf4j
public class RepositoryBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware, ApplicationListener<ContextRefreshedEvent> {

    /**
     * Spring 应用上下文，用于查找 {@link RepositoryDelegateFactory} Bean
     */
    private ApplicationContext applicationContext;

    /**
     * 已收集的 Delegate 元信息列表（按收集顺序，查找时按 priority 降序排序）
     */
    private final List<DelegateInfo> delegateInfos = new ArrayList<>();

    /**
     * 已识别的 RepositoryFacade 信息列表
     */
    private final List<RepositoryFacadeInfo> facadeInfos = new ArrayList<>();

    /**
     * 注入 Spring 应用上下文
     *
     * @param applicationContext Spring 应用上下文
     * @throws BeansException 注入失败时抛出
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Bean 初始化前置处理：收集阶段
     * <p>
     * 检测 Bean 是否带有 {@link DelegateFor} 注解，若有则提取元信息：
     * <ul>
     *   <li>若是 {@link RepositoryDelegate}，同时记录 delegate 和 queryDelegate 引用</li>
     *   <li>若是 {@link IQueryDelegate}（仅实现查询接口），记录 queryDelegate 引用</li>
     * </ul>
     *
     * @param bean     待处理的 Bean 实例
     * @param beanName Bean 名称
     * @return 原始 Bean（不进行任何修改）
     * @throws BeansException 处理异常时抛出
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        DelegateFor annotation = bean.getClass().getAnnotation(DelegateFor.class);
        if (annotation != null) {
            // 提取 @DelegateFor 注解的元数据
            DelegateInfo info = new DelegateInfo();
            info.beanName = beanName;
            info.name = annotation.name();
            info.type = annotation.type();
            info.poClass = annotation.po();
            info.priority = annotation.priority();
            info.description = annotation.description();
            info.delegateClass = bean.getClass();
            info.delegateType = annotation.delegateType();

            // 同时是 RepositoryDelegate 的，记录完整 delegate 引用
            if (bean instanceof RepositoryDelegate) {
                info.delegate = (RepositoryDelegate<?, ?>) bean;
                if (bean instanceof IQueryDelegate) {
                    info.queryDelegate = (IQueryDelegate<?, ?>) bean;
                }
                log.info("Found RepositoryDelegate: name={}, type={}, delegateType={}, poClass={}, delegateClass={}, priority={}",
                        info.name, info.type, info.delegateType,
                        info.poClass != null ? info.poClass.getSimpleName() : "null",
                        info.delegateClass.getSimpleName(),
                        info.priority);
            }
            // 仅实现 IQueryDelegate（如专用的读代理），仅记录 queryDelegate 引用
            if (bean instanceof IQueryDelegate) {
                info.queryDelegate = (IQueryDelegate<?, ?>) bean;
                log.info("Found IQueryDelegate: name={}, type={}, delegateType={}, poClass={}, delegateClass={}, priority={}",
                        info.name, info.type, info.delegateType,
                        info.poClass != null ? info.poClass.getSimpleName() : "null",
                        info.delegateClass.getSimpleName(),
                        info.priority);
            }
            delegateInfos.add(info);
        }
        return bean;
    }

    /**
     * Bean 初始化后置处理：识别阶段
     * <p>
     * 识别 {@link RepositoryFacade} 实例并记录引用，待容器刷新时统一注入 Delegate。
     *
     * @param bean     待处理的 Bean 实例
     * @param beanName Bean 名称
     * @return 原始 Bean
     * @throws BeansException 处理异常时抛出
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof RepositoryFacade) {
            RepositoryFacadeInfo info = new RepositoryFacadeInfo();
            info.facade = (RepositoryFacade<?, ?, ?, ?>) bean;
            info.beanName = beanName;
            facadeInfos.add(info);
        }
        return bean;
    }

    /**
     * 容器刷新事件处理：注入阶段
     * <p>
     * 容器刷新完成后，遍历所有 Facade，依次执行 Delegate 匹配与注入。
     *
     * @param event 容器刷新事件
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        for (RepositoryFacadeInfo facadeInfo : facadeInfos) {
            injectDelegatesToFacade(facadeInfo.facade, facadeInfo.beanName);
        }
    }

    /**
     * 为单个 RepositoryFacade 注入 Delegate
     * <p>
     * 完整流程：
     * <ol>
     *   <li>解析 Facade 子类的 4 个泛型参数（entity/id/po/delegateClass）</li>
     *   <li>读取 @Repository 注解配置（type/cqrs/readDelegateClass）</li>
     *   <li>查找 BASE Delegate：先匹配用户自定义 → 再尝试工厂自动创建 → 最后回退 InMemory</li>
     *   <li>当 cqrs=true 且指定 readDelegateClass 时，查找 READ Delegate</li>
     *   <li>注入 entityClass/poClass 到 Facade</li>
     * </ol>
     *
     * @param facade   待注入的 RepositoryFacade
     * @param beanName Facade 的 Bean 名称
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void injectDelegatesToFacade(RepositoryFacade facade, String beanName) {
        try {
            // 步骤 1：解析 Facade 子类继承 RepositoryFacade 时的泛型实参
            Class<?>[] genericTypes = getGenericTypes(facade.getClass());
            if (genericTypes.length < 4) {
                log.debug("RepositoryFacade '{}' has insufficient generic types (need 4, got {})", beanName, genericTypes.length);
                return;
            }

            Class<?> entityClass = genericTypes[0];
            Class<?> idClass = genericTypes[1];
            Class<?> poClass = genericTypes[2];
            Class<?> delegateClass = genericTypes[3];

            // 步骤 2：读取 @Repository 注解配置（type/cqrs/readDelegateClass）
            Repository repositoryAnnotation = facade.getClass().getAnnotation(Repository.class);
            RepositoryType targetType = repositoryAnnotation != null ? repositoryAnnotation.type() : RepositoryType.AUTO;
            boolean cqrsEnabled = repositoryAnnotation != null && repositoryAnnotation.cqrs();
            Class<?> readDelegateClass = repositoryAnnotation != null ? repositoryAnnotation.readDelegateClass() : Object.class;

            log.debug("RepositoryFacade '{}' requires: entity={}, id={}, po={}, delegateClass={}, type={}, cqrs={}, readDelegateClass={}",
                    beanName, entityClass.getSimpleName(), idClass.getSimpleName(),
                    poClass.getSimpleName(), delegateClass.getSimpleName(), targetType, cqrsEnabled,
                    readDelegateClass != null ? readDelegateClass.getSimpleName() : "null");

            // 步骤 3：查找 BASE Delegate（6 步匹配）
            DelegateInfo baseDelegateInfo = findBaseDelegate(beanName, poClass, delegateClass, targetType);

            if (baseDelegateInfo != null && baseDelegateInfo.delegate != null) {
                // 3a. 命中用户自定义 Delegate，直接注入
                facade.setBaseDelegate(baseDelegateInfo.delegate);
                log.info("Injected BASE delegate '{}' (type={}) into RepositoryFacade '{}'",
                        baseDelegateInfo.beanName, baseDelegateInfo.type, beanName);
            } else {
                // 3b. 未命中，尝试通过 RepositoryDelegateFactory 自动创建
                log.debug("No matching BASE delegate found for RepositoryFacade '{}', trying to auto-create delegate via factory", beanName);
                RepositoryDelegate autoDelegate = autoCreateDelegate(poClass, idClass, targetType);

                if (autoDelegate != null) {
                    facade.setBaseDelegate(autoDelegate);
                    log.info("Auto-created BASE delegate (type={}) for RepositoryFacade '{}'", targetType, beanName);
                } else {
                    // 3c. 工厂也无法创建，回退到 InMemoryRepositoryDelegate（仅用于开发/测试）
                    log.warn("No matching BASE delegate found for RepositoryFacade '{}', using default InMemoryRepositoryDelegate", beanName);
                    RepositoryDelegate defaultDelegate = new InMemoryRepositoryDelegate(poClass);
                    facade.setBaseDelegate(defaultDelegate);
                }
            }

            // 步骤 4：CQRS 模式下查找 READ Delegate
            boolean shouldEnableReadDelegate = cqrsEnabled && readDelegateClass != null && readDelegateClass != Object.class;
            if (shouldEnableReadDelegate) {
                // READ 代理使用 AUTO 类型匹配，因为 CQRS 模式下读代理可能与写代理类型不同
                // 例如：写代理用 MyBatis Plus，读代理用 Elasticsearch
                DelegateInfo readDelegateInfo = findReadDelegate(beanName, poClass, readDelegateClass, RepositoryType.AUTO);

                if (readDelegateInfo != null && readDelegateInfo.queryDelegate != null) {
                    facade.setReadDelegate(readDelegateInfo.queryDelegate);
                    log.info("Injected READ delegate '{}' (type={}) into RepositoryFacade '{}'",
                            readDelegateInfo.beanName, readDelegateInfo.type, beanName);
                } else {
                    log.warn("No matching READ delegate found for RepositoryFacade '{}' (readDelegateClass={}), read operations will use BASE delegate",
                            beanName, readDelegateClass.getSimpleName());
                }
            }

            // 步骤 5：注入 entityClass/poClass，供 Facade 做 Entity ↔ PO 反射转换
            facade.setEntityClass(entityClass);
            facade.setPoClass(poClass);

        } catch (Exception e) {
            log.warn("Error injecting delegates to RepositoryFacade '{}': {}", beanName, e.getMessage());
        }
    }

    /**
     * 通过 {@link RepositoryDelegateFactory} 自动创建 Delegate
     * <p>
     * 当容器中存在工厂 Bean 且无自定义 Delegate 时使用。
     * <ul>
     *   <li>指定了非 AUTO 类型：仅向同类型工厂请求创建</li>
     *   <li>指定为 AUTO 类型：依次尝试所有工厂，首个成功即返回</li>
     * </ul>
     *
     * @param poClass    PO 类型
     * @param idClass    主键类型
     * @param targetType 期望的仓储类型
     * @return 创建成功的 Delegate，无法创建时返回 null
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private RepositoryDelegate autoCreateDelegate(Class<?> poClass, Class<?> idClass, RepositoryType targetType) {
        try {
            Map<String, RepositoryDelegateFactory> factories = applicationContext.getBeansOfType(RepositoryDelegateFactory.class);

            if (factories.isEmpty()) {
                log.debug("No RepositoryDelegateFactory beans found in application context");
                return null;
            }

            // 指定具体类型：仅匹配同类型工厂
            if (targetType != RepositoryType.AUTO) {
                for (RepositoryDelegateFactory factory : factories.values()) {
                    if (factory.getType() == targetType) {
                        RepositoryDelegate delegate = factory.createDelegate(poClass, idClass);
                        if (delegate != null) {
                            return delegate;
                        }
                    }
                }
            } else {
                // AUTO 类型：依次尝试所有工厂，首个成功即返回
                for (RepositoryDelegateFactory factory : factories.values()) {
                    try {
                        RepositoryDelegate delegate = factory.createDelegate(poClass, idClass);
                        if (delegate != null) {
                            log.debug("Auto-created delegate using factory for type: {}", factory.getType());
                            return delegate;
                        }
                    } catch (Exception e) {
                        log.debug("Factory {} failed to create delegate: {}", factory.getType(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to auto-create delegate: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 查找 BASE Delegate（6 步匹配，按优先级从严格到宽松）
     * <p>
     * 候选集合：delegateType == BASE 且 delegate != null，按 priority 降序排序。
     * 匹配步骤（命中即返回，越靠前匹配条件越严格）：
     * <ol>
     *   <li><b>delegateClass 兼容 + name 匹配 + type 匹配</b>（最严格，三者全中）</li>
     *   <li><b>delegateClass 兼容 + type 匹配</b>（忽略 name）</li>
     *   <li><b>delegateClass 兼容 + name 匹配</b>（忽略 type）</li>
     *   <li><b>仅 delegateClass 兼容</b>（仅按类型）</li>
     *   <li><b>仅 name 匹配</b>（仅按名称）</li>
     *   <li><b>仅 poClass 匹配</b>（最宽松，按 PO 类型兜底）</li>
     * </ol>
     *
     * @param beanName      Facade 的 Bean 名称
     * @param poClass       PO 类型
     * @param delegateClass Delegate 类型（Facade 第四个泛型实参）
     * @param targetType    期望的仓储类型（AUTO 表示不限制）
     * @return 匹配的 DelegateInfo，无匹配时返回 null
     */
    private DelegateInfo findBaseDelegate(String beanName, Class<?> poClass, Class<?> delegateClass, RepositoryType targetType) {
        // 候选集合：BASE 类型且 delegate 已就绪，按 priority 降序
        List<DelegateInfo> filtered = delegateInfos.stream()
                .filter(info -> info.delegateType == DelegateType.BASE && info.delegate != null)
                .sorted(Comparator.comparingInt((DelegateInfo i) -> i.priority).reversed())
                .toList();

        // 第 1 步：delegateClass 兼容 + name 匹配 + type 匹配（最严格）
        for (DelegateInfo info : filtered) {
            if (delegateClass.isAssignableFrom(info.delegateClass)
                    && info.name != null && !info.name.isEmpty() && info.name.equals(beanName)
                    && (targetType == RepositoryType.AUTO || targetType == info.type)) {
                return info;
            }
        }

        // 第 2 步：delegateClass 兼容 + type 匹配（忽略 name）
        for (DelegateInfo info : filtered) {
            if (delegateClass.isAssignableFrom(info.delegateClass)
                    && (targetType == RepositoryType.AUTO || targetType == info.type)) {
                return info;
            }
        }

        // 第 3 步：delegateClass 兼容 + name 匹配（忽略 type）
        for (DelegateInfo info : filtered) {
            if (delegateClass.isAssignableFrom(info.delegateClass)
                    && info.name != null && !info.name.isEmpty() && info.name.equals(beanName)) {
                return info;
            }
        }

        // 第 4 步：仅 delegateClass 兼容
        for (DelegateInfo info : filtered) {
            if (delegateClass.isAssignableFrom(info.delegateClass)) {
                return info;
            }
        }

        // 第 5 步：仅 name 匹配
        for (DelegateInfo info : filtered) {
            if (info.name != null && !info.name.isEmpty() && info.name.equals(beanName)) {
                return info;
            }
        }

        // 第 6 步：仅 poClass 匹配（最宽松兜底）
        for (DelegateInfo info : filtered) {
            if (info.poClass != null && info.poClass.equals(poClass)) {
                return info;
            }
        }

        return null;
    }

    /**
     * 查找 READ Delegate（6 步匹配，与 {@link #findBaseDelegate} 结构一致）
     * <p>
     * 候选集合：delegateType == READ 且 queryDelegate != null，按 priority 降序排序。
     * 用于 CQRS 模式下匹配读代理，匹配步骤与 BASE 完全一致，区别仅在于候选类型。
     *
     * @param beanName          Facade 的 Bean 名称
     * @param poClass           PO 类型
     * @param readDelegateClass 读代理类型（@Repository#readDelegateClass）
     * @param targetType        期望的仓储类型
     * @return 匹配的 DelegateInfo，无匹配时返回 null
     */
    private DelegateInfo findReadDelegate(String beanName, Class<?> poClass, Class<?> readDelegateClass, RepositoryType targetType) {
        // 候选集合：READ 类型且 queryDelegate 已就绪，按 priority 降序
        List<DelegateInfo> filtered = delegateInfos.stream()
                .filter(info -> info.delegateType == DelegateType.READ && info.queryDelegate != null)
                .sorted(Comparator.comparingInt((DelegateInfo i) -> i.priority).reversed())
                .toList();

        // 第 1 步：readDelegateClass 兼容 + name 匹配 + type 匹配
        for (DelegateInfo info : filtered) {
            if (readDelegateClass.isAssignableFrom(info.delegateClass)
                    && info.name != null && !info.name.isEmpty() && info.name.equals(beanName)
                    && (targetType == RepositoryType.AUTO || targetType == info.type)) {
                return info;
            }
        }

        // 第 2 步：readDelegateClass 兼容 + type 匹配
        for (DelegateInfo info : filtered) {
            if (readDelegateClass.isAssignableFrom(info.delegateClass)
                    && (targetType == RepositoryType.AUTO || targetType == info.type)) {
                return info;
            }
        }

        // 第 3 步：readDelegateClass 兼容 + name 匹配
        for (DelegateInfo info : filtered) {
            if (readDelegateClass.isAssignableFrom(info.delegateClass)
                    && info.name != null && !info.name.isEmpty() && info.name.equals(beanName)) {
                return info;
            }
        }

        // 第 4 步：仅 readDelegateClass 兼容
        for (DelegateInfo info : filtered) {
            if (readDelegateClass.isAssignableFrom(info.delegateClass)) {
                return info;
            }
        }

        // 第 5 步：仅 name 匹配
        for (DelegateInfo info : filtered) {
            if (info.name != null && !info.name.isEmpty() && info.name.equals(beanName)) {
                return info;
            }
        }

        // 第 6 步：仅 poClass 匹配
        for (DelegateInfo info : filtered) {
            if (info.poClass != null && info.poClass.equals(poClass)) {
                return info;
            }
        }

        return null;
    }

    /**
     * 反射解析类继承 RepositoryFacade 时的泛型实参
     * <p>
     * 沿父类链向上查找首个参数化继承 RepositoryFacade 的位置，
     * 返回其实际类型参数数组（长度应为 4：entity/id/po/delegate）。
     * 支持嵌套 ParameterizedType 场景。
     *
     * @param clazz 待解析的类
     * @return 泛型实参数组，无法解析时返回空数组
     */
    private Class<?>[] getGenericTypes(Class<?> clazz) {
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            Type superclass = currentClass.getGenericSuperclass();
            if (superclass instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) superclass;
                Type rawType = parameterizedType.getRawType();
                // 定位到 RepositoryFacade 的参数化父类
                if (rawType instanceof Class && RepositoryFacade.class.isAssignableFrom((Class<?>) rawType)) {
                    Type[] typeArgs = parameterizedType.getActualTypeArguments();
                    Class<?>[] classes = new Class[typeArgs.length];
                    for (int i = 0; i < typeArgs.length; i++) {
                        // 普通类型实参直接使用
                        if (typeArgs[i] instanceof Class) {
                            classes[i] = (Class<?>) typeArgs[i];
                        } else if (typeArgs[i] instanceof ParameterizedType) {
                            // 嵌套泛型（如 Delegate<UserPO, Long>）取其原始类型
                            Type raw = ((ParameterizedType) typeArgs[i]).getRawType();
                            if (raw instanceof Class) {
                                classes[i] = (Class<?>) raw;
                            }
                        }
                    }
                    return classes;
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return new Class<?>[0];
    }

    /**
     * Delegate 元信息内部载体
     * <p>
     * 封装从 {@link DelegateFor} 注解提取的所有元数据，以及对应的 Bean 引用。
     */
    private static class DelegateInfo {
        /** Bean 名称 */
        String beanName;
        /** @DelegateFor#name() 声明的仓储名称 */
        String name;
        /** 存储类型 */
        RepositoryType type;
        /** PO 类型 */
        Class<?> poClass;
        /** 优先级（数字越大越优先） */
        int priority;
        /** 描述信息 */
        String description;
        /** Delegate 实现类 */
        Class<?> delegateClass;
        /** RepositoryDelegate 引用（若 Bean 实现了该接口） */
        RepositoryDelegate<?, ?> delegate;
        /** IQueryDelegate 引用（若 Bean 实现了该接口） */
        IQueryDelegate<?, ?> queryDelegate;
        /** 委托类型（BASE/READ） */
        DelegateType delegateType;
    }

    /**
     * RepositoryFacade 元信息内部载体
     * <p>
     * 封装 Facade 实例及其 Bean 名称，供容器刷新时批量注入使用。
     */
    private static class RepositoryFacadeInfo {
        /** Facade 实例 */
        RepositoryFacade<?, ?, ?, ?> facade;
        /** Bean 名称 */
        String beanName;
    }
}