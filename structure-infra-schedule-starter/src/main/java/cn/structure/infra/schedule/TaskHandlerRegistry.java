/*
Copyright 2023 Structure Projects

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	`http://www.apache.org/licenses/LICENSE-2.0`

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package cn.structure.infra.schedule;

/**
 * 任务处理器注册表接口。
 *
 * <p>该接口负责维护 {@code handlerName} 到 {@link TaskHandler} 实例的映射关系，
 * 是 {@link TaskScheduler} 与具体业务执行逻辑之间的桥梁。调度器在调度任务前会先
 * 通过 {@link #contains(String)} 校验 handler 是否已注册，触发任务时通过
 * {@link #get(String)} 查找并执行对应的 handler。</p>
 *
 * <p><b>设计意图：</b>将 handler 的注册、查找、卸载等管理职责从调度器中剥离，
 * 形成单一职责的注册表，便于业务方在任意阶段动态注册/卸载 handler，
 * 也便于扩展为基于 Spring 容器或其他自定义发现机制的实现。</p>
 *
 * <p>已知实现：{@link DefaultTaskHandlerRegistry}（基于 {@code ConcurrentHashMap} 的默认实现）</p>
 */
public interface TaskHandlerRegistry {

    /**
     * 注册任务处理器。
     *
     * <p>若 {@code handlerName} 已存在，则覆盖原有 handler。</p>
     *
     * @param handlerName 处理器名称，作为唯一标识，不能为 {@code null}
     * @param handler     处理器实例，不能为 {@code null}
     * @throws IllegalArgumentException 当 handlerName 或 handler 为 {@code null} 时抛出
     */
    void register(String handlerName, TaskHandler handler);

    /**
     * 根据处理器名称获取任务处理器。
     *
     * @param handlerName 处理器名称
     * @return 对应的处理器实例；若未注册则返回 {@code null}
     */
    TaskHandler get(String handlerName);

    /**
     * 注销指定名称的任务处理器。
     *
     * <p>若 {@code handlerName} 未注册则静默忽略，不会抛出异常。</p>
     *
     * @param handlerName 处理器名称
     */
    void unregister(String handlerName);

    /**
     * 判断指定名称的任务处理器是否已注册。
     *
     * <p>调度器在调度任务前会调用此方法做前置校验。</p>
     *
     * @param handlerName 处理器名称
     * @return 已注册返回 {@code true}，否则返回 {@code false}
     */
    boolean contains(String handlerName);
}