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

import lombok.Data;

import java.util.concurrent.TimeUnit;

/**
 * 缓存配置
 * <p>
 * 定义低代码仓储的缓存策略，启用后查询数据会自动缓存以提升性能。
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@Data
public class CacheConfig {

    /**
     * 是否启用缓存
     */
    private boolean enabled;

    /**
     * 缓存过期时间
     */
    private long ttl = 300;

    /**
     * 时间单位
     */
    private TimeUnit timeUnit = TimeUnit.SECONDS;
}
