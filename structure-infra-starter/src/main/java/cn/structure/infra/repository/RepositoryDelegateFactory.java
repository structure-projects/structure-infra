package cn.structure.infra.repository;

import cn.structure.infra.repository.RepositoryType;

/**
 * 仓储委托工厂接口
 * <p>
 * 各个持久化技术的 starter 模块实现此接口，
 * 用于自动创建对应的 RepositoryDelegate 实例。
 * <p>
 * 基础模块通过 Spring 容器查找所有实现类，
 * 当找不到用户自定义的 delegate 时，使用工厂自动创建。
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
public interface RepositoryDelegateFactory {

    /**
     * 获取支持的仓储类型
     *
     * @return 仓储类型
     */
    RepositoryType getType();

    /**
     * 创建仓储委托实例
     *
     * @param poClass PO 类
     * @param idClass ID 类
     * @return 仓储委托实例，如果无法创建则返回 null
     */
    RepositoryDelegate<?, ?> createDelegate(Class<?> poClass, Class<?> idClass);
}
