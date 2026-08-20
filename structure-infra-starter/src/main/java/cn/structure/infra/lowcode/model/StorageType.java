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

package cn.structure.infra.lowcode.model;

/**
 * 存储类型枚举
 * <p>
 * 定义低代码仓储支持的存储引擎类型，用于路由到对应的仓储实现。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
public enum StorageType {

    /**
     * MySQL 关系型数据库
     */
    MYSQL,

    /**
     * MongoDB 文档数据库
     */
    MONGODB,

    /**
     * Elasticsearch 搜索引擎
     */
    ELASTICSEARCH,

    /**
     * Redis 缓存数据库
     */
    REDIS,

    /**
     * 内存存储（用于测试或临时数据）
     */
    IN_MEMORY
}
