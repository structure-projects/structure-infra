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

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link TaskHandlerRegistry} 的默认实现，基于 {@link ConcurrentHashMap} 维护 handler 映射。
 *
 * <p><b>设计意图：</b>使用 {@code ConcurrentHashMap} 保证多线程并发注册/查找/注销时的线程安全，
 * 满足调度器在多线程触发场景下对 handler 的并发访问需求。该实现由
 * {@code AutoScheduleConfiguration} 在容器中不存在自定义 {@link TaskHandlerRegistry}
 * 时作为默认 Bean 装配。</p>
 *
 * <p><b>协作关系：</b>由 {@link LocalThreadTaskScheduler} 持有引用，调度前调用
 * {@link #contains(String)} 校验、调度时调用 {@link #get(String)} 取出 handler 执行。</p>
 */
@Slf4j
public class DefaultTaskHandlerRegistry implements TaskHandlerRegistry {

    /**
     * handler 名称到 handler 实例的并发映射表。
     */
    private final Map<String, TaskHandler> handlerMap = new ConcurrentHashMap<>();

    /**
     * 注册任务处理器。
     *
     * <p>若 {@code handlerName} 已存在，则覆盖原有 handler。</p>
     *
     * @param handlerName 处理器名称，不能为 {@code null}
     * @param handler     处理器实例，不能为 {@code null}
     * @throws IllegalArgumentException 当 handlerName 或 handler 为 {@code null} 时抛出
     */
    @Override
    public void register(String handlerName, TaskHandler handler) {
        if (handlerName == null || handler == null) {
            throw new IllegalArgumentException("Handler name and handler cannot be null");
        }
        handlerMap.put(handlerName, handler);
        log.info("Registered task handler: {}", handlerName);
    }

    /**
     * 根据处理器名称获取任务处理器。
     *
     * @param handlerName 处理器名称
     * @return 对应的处理器实例；若未注册则返回 {@code null}
     */
    @Override
    public TaskHandler get(String handlerName) {
        return handlerMap.get(handlerName);
    }

    /**
     * 注销指定名称的任务处理器。
     *
     * <p>若 {@code handlerName} 未注册则静默忽略。</p>
     *
     * @param handlerName 处理器名称
     */
    @Override
    public void unregister(String handlerName) {
        handlerMap.remove(handlerName);
        log.info("Unregistered task handler: {}", handlerName);
    }

    /**
     * 判断指定名称的任务处理器是否已注册。
     *
     * @param handlerName 处理器名称
     * @return 已注册返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean contains(String handlerName) {
        return handlerMap.containsKey(handlerName);
    }
}