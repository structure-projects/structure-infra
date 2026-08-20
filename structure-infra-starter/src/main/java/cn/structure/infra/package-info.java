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

/**
 * structure-infra-starter 根包，DDD 仓储抽象层的核心模块。
 * <p>
 * 本模块基于 Facade + Delegate 模式构建领域层与持久化层之间的防腐层（ACL），
 * 提供统一的 CRUD 操作契约、CQRS 读写分离、自动 Delegate 装配等能力。
 * <p>
 * 核心子包说明：
 * <ul>
 *   <li>{@link cn.structure.infra.annotations} —— 仓储相关注解（{@code @Repository}、{@code @DelegateFor}）</li>
 *   <li>{@link cn.structure.infra.repository} —— 仓储 Facade/Delegate 抽象与装配核心</li>
 *   <li>{@link cn.structure.infra.configuration} —— Spring Boot 自动装配配置类</li>
 *   <li>{@link cn.structure.infra.properties} —— 框架级配置属性</li>
 *   <li>{@link cn.structure.infra.event} —— 事件子系统（EventManager / Event / EventChannel）</li>
 *   <li>{@link cn.structure.infra.lowcode} —— 低代码仓储子系统（动态资源 schema、存储路由）</li>
 * </ul>
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
package cn.structure.infra;
