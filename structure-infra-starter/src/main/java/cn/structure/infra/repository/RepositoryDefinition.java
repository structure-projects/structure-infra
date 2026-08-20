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

package cn.structure.infra.repository;

import lombok.Data;

/**
 * 仓储定义元数据
 * <p>
 * 封装仓储的配置信息。
 * <p>
 * 注意：PO 类型已从 Facade 层移除，Entity ↔ PO 转换由 Delegate 实现负责。
 *
 * @author chuck
 * @version 1.0.2
 * @since 2026/6/28
 */
@Data
public class RepositoryDefinition {

    /**
     * 仓储名称（Bean名称）
     */
    private String name;

    /**
     * 仓储类型
     */
    private RepositoryType type;

    /**
     * 实体类类型
     */
    private Class<?> entityClass;

    /**
     * 主键类型
     */
    private Class<?> idClass;

    /**
     * 仓储描述
     */
    private String description;

    /**
     * 是否启用 CQRS 读写分离
     * <p>
     * 启用后，读操作使用 readDelegate，写操作使用 baseDelegate
     * <p>
     * 必须与 readDelegateClass 同时配置才生效
     */
    private boolean cqrs;

    /**
     * 读代理类
     * <p>
     * 指定读操作使用的代理类，用于 CQRS 读写分离
     * <p>
     * 必须与 cqrs=true 同时配置才生效
     */
    private Class<?> readDelegateClass;

    /**
     * 验证配置是否有效
     *
     * @return true if valid
     */
    public boolean isValid() {
        return entityClass != null && entityClass != Object.class;
    }

}