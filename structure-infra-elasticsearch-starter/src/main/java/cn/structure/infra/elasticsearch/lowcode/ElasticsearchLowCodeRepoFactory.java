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

package cn.structure.infra.elasticsearch.lowcode;

import cn.structure.infra.lowcode.model.RepositoryConfig;
import cn.structure.infra.lowcode.model.ResourceSchema;
import cn.structure.infra.lowcode.model.StorageType;
import cn.structure.infra.lowcode.repository.LowCodeRepoFactory;
import cn.structure.infra.lowcode.repository.LowCodeStorage;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

/**
 * Elasticsearch 低代码仓储工厂
 * <p>
 * 负责创建 Elasticsearch 类型的低代码存储实例，内部使用 ElasticsearchOperations + Map 执行动态操作。
 * <p>
 * 核心特性：
 * <ul>
 *   <li>Map 动态操作：使用 Map 代替 POJO，无需定义实体类</li>
 *   <li>自动创建索引：初始化时自动创建索引</li>
 *   <li>自动填充：支持创建时间、更新时间自动填充</li>
 * </ul>
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
public class ElasticsearchLowCodeRepoFactory implements LowCodeRepoFactory {

    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 通过 ElasticsearchOperations 构造
     *
     * @param elasticsearchOperations ElasticsearchOperations 实例
     */
    public ElasticsearchLowCodeRepoFactory(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    /**
     * 返回该工厂支持的存储类型，用于低代码路由引擎匹配。
     *
     * @return 固定返回 {@link StorageType#ELASTICSEARCH}
     */
    @Override
    public StorageType getType() {
        return StorageType.ELASTICSEARCH;
    }

    /**
     * 创建 Elasticsearch 低代码存储实例。
     * <p>
     * 内部构造 {@link ElasticsearchLowCodeStorage}，由其在初始化时自动创建索引（不创建 mapping）。
     *
     * @param schema 资源 schema 定义（索引名、字段、主键等）
     * @param config 仓储配置（当前实现未使用，保留以匹配 SPI 签名）
     * @return 低代码存储实例
     */
    @Override
    public LowCodeStorage createStorage(ResourceSchema schema, RepositoryConfig config) {
        return new ElasticsearchLowCodeStorage(schema, elasticsearchOperations);
    }
}