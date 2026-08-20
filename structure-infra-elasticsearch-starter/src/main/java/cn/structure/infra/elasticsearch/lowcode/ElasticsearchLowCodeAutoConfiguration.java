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

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

/**
 * Elasticsearch 低代码自动配置类
 * <p>
 * 当低代码功能启用且存在 ElasticsearchOperations 时，自动注册 Elasticsearch 低代码仓储工厂，
 * 使低代码路由引擎能够创建 Elasticsearch 类型的存储实例。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "structure.infra.lowcode", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchLowCodeAutoConfiguration {

    /**
     * 注册 Elasticsearch 低代码仓储工厂
     *
     * @param elasticsearchOperations ElasticsearchOperations 实例
     * @return Elasticsearch 低代码仓储工厂实例
     */
    @Bean
    public ElasticsearchLowCodeRepoFactory elasticsearchLowCodeRepoFactory(ElasticsearchOperations elasticsearchOperations) {
        return new ElasticsearchLowCodeRepoFactory(elasticsearchOperations);
    }
}