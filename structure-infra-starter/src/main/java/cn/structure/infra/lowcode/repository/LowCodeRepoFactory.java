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

package cn.structure.infra.lowcode.repository;

import cn.structure.infra.lowcode.model.RepositoryConfig;
import cn.structure.infra.lowcode.model.ResourceSchema;
import cn.structure.infra.lowcode.model.StorageType;

/**
 * 低代码仓储工厂接口
 * <p>
 * 定义低代码仓储工厂的契约，每种存储引擎（MySQL、MongoDB、Elasticsearch 等）
 * 都需要提供对应的工厂实现，负责创建具体的 {@link LowCodeStorage} 实例。
 * <p>
 * 工厂实例由 Spring 容器管理，路由引擎通过 {@link StorageType} 查找对应的工厂。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
public interface LowCodeRepoFactory {

    /**
     * 获取存储类型
     *
     * @return 存储类型枚举
     */
    StorageType getType();

    /**
     * 创建低代码存储实例
     *
     * @param schema 资源 schema 定义
     * @param config 仓储配置
     * @return 存储实例
     */
    LowCodeStorage createStorage(ResourceSchema schema, RepositoryConfig config);
}
